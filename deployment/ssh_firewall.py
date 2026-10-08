"""Manage only the tagged deployment SSH /32; permanent rules stay operator-owned."""
import ipaddress
import json
import os
import re
import socket
import subprocess
import sys
import time

TAG = 'kairos-deploy-ssh'


def aws(action, **options):
    command = ['aws', 'lightsail', action, '--region', os.environ['AWS_REGION'],
               '--output', 'json', '--no-cli-pager', '--cli-connect-timeout', '10',
               '--cli-read-timeout', '20']
    for key, value in options.items():
        command.extend(['--' + key.replace('_', '-'), value])
    result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=120)
    response = json.loads(result.stdout)
    operation = response.get('operation')
    if operation and action != 'get-operation':
        for attempt in range(12):
            if operation['isTerminal']:
                if operation['status'] not in ('Succeeded', 'Completed'):
                    raise OSError('Lightsail operation failed')
                break
            if attempt == 11:
                raise TimeoutError('Lightsail operation remained pending')
            time.sleep(5)
            operation = aws('get-operation', operation_id=operation['id'])['operation']
    return response


def public_host(value):
    address = ipaddress.ip_address(value)
    if address.version != 4 or not address.is_global or address.is_multicast:
        raise ValueError('a public IPv4 address is required')
    return address


class Firewall:
    def __init__(self, instance, admin_cidrs, run, host):
        if not re.fullmatch(r'[\w-]+', instance) or not re.fullmatch(r'\d+-\d+', run):
            raise ValueError('invalid instance or run identity')
        self.instance, self.run, self.host = instance, run, str(public_host(host))
        self.admin = [ipaddress.ip_network(value.strip(), strict=True)
                      for value in admin_cidrs.split(',') if value.strip()]
        if not self.admin or any(n.version != 4 or n.prefixlen == 0 for n in self.admin):
            raise ValueError('explicit administrator IPv4 CIDRs are required')

    def instance_state(self):
        instance = aws('get-instance', instance_name=self.instance)['instance']
        if instance['publicIpAddress'] != self.host or instance.get('ipv6Addresses'):
            raise ValueError('target must match DEPLOY_HOST and remain IPv4-only')
        return instance

    def marker(self):
        tags = self.instance_state().get('tags', [])
        value = next((tag['value'] for tag in tags if tag['key'] == TAG), None)
        if value is None:
            return None
        match = re.fullmatch(r'(\d+-\d+):([0-9.]+/32)', value)
        if not match:
            raise ValueError('invalid deployment marker; operator recovery required')
        run, cidr = match.groups()
        address = public_host(cidr.removesuffix('/32'))
        if any(address in network for network in self.admin):
            raise ValueError('refusing to alter an administrator address')
        return run, cidr

    def ssh_sources(self):
        rules = aws('get-instance-port-states', instance_name=self.instance)['portStates']
        sources = []
        for rule in rules:
            if rule['protocol'] in ('tcp', 'all') and rule['fromPort'] <= 22 <= rule['toPort']:
                sources.extend(rule.get('cidrs', []))
        return sources

    def wait(self, predicate):
        for attempt in range(12):
            if predicate():
                return
            if attempt < 11:
                time.sleep(5)
        raise TimeoutError('Lightsail firewall update did not become visible')

    def cleanup(self, recover=False, keep_marker=False):
        marker = self.marker()
        if marker is None:
            return
        run, cidr = marker
        if not recover and run != self.run:
            raise ValueError('refusing to clean a different deployment run')
        # An accepted open can still be pending even when its CIDR is not visible.
        self.wait(lambda: all(operation['isTerminal'] for operation in
                  aws('get-operations-for-resource', resource_name=self.instance)['operations']
                  if operation['operationType'] == 'OpenInstancePublicPorts'))
        port = json.dumps({'fromPort': 22, 'toPort': 22, 'protocol': 'tcp',
                           'cidrs': [cidr], 'ipv6Cidrs': [], 'cidrListAliases': []})
        # Retain the marker until absence is verified, including after AWS errors.
        if cidr in self.ssh_sources():
            aws('close-instance-public-ports', instance_name=self.instance, port_info=port)
        self.wait(lambda: cidr not in self.ssh_sources())
        if keep_marker:
            return
        aws('untag-resource', resource_name=self.instance, tag_keys=TAG)
        self.wait(lambda: self.marker() is None)

    def open(self, ip):
        address = public_host(ip)
        if any(address in network for network in self.admin):
            raise ValueError('runner address overlaps administrator access')
        self.cleanup(recover=True)
        if any(address in ipaddress.ip_network(cidr) for cidr in self.ssh_sources()):
            raise ValueError('runner already has unmarked SSH access; operator review required')
        cidr = f'{address}/32'
        value = f'{self.run}:{cidr}'
        aws('tag-resource', resource_name=self.instance,
            tags=json.dumps([{'key': TAG, 'value': value}]))
        self.wait(lambda: self.marker() == (self.run, cidr))
        aws('open-instance-public-ports', instance_name=self.instance,
            port_info=json.dumps({'fromPort': 22, 'toPort': 22, 'protocol': 'tcp',
                                  'cidrs': [cidr], 'ipv6Cidrs': [], 'cidrListAliases': []}))
        self.wait(lambda: cidr in self.ssh_sources())


def main():
    try:
        firewall = Firewall(os.environ['DEPLOY_INSTANCE'], os.environ['DEPLOY_ADMIN_CIDRS'],
                            f"{os.environ['GITHUB_RUN_ID']}-{os.environ['GITHUB_RUN_ATTEMPT']}",
                            os.environ['DEPLOY_HOST'])
        if sys.argv[1] == 'open':
            ip = subprocess.check_output(['curl', '--fail', '--silent', '--show-error',
                                          '--ipv4', '--noproxy', '*', '--max-time', '20',
                                          'https://checkip.amazonaws.com'], text=True).strip()
            firewall.open(ip)
            for attempt in range(12):
                try:
                    with socket.create_connection((firewall.host, 22), timeout=5):
                        break
                except OSError:
                    if attempt == 11:
                        raise
                    time.sleep(5)
            print('Temporary SSH access ready.')
        elif sys.argv[1] in ('close', 'recover'):
            # AWS CLI handles transient API retries; retry the whole cleanup too.
            for attempt in range(3):
                try:
                    firewall.cleanup(recover=sys.argv[1] == 'recover',
                                     keep_marker=os.environ.get('FIREWALL_OPEN_OUTCOME', '') not in ('', 'success'))
                    break
                except (OSError, subprocess.SubprocessError, TimeoutError):
                    if attempt == 2:
                        raise
                    time.sleep(5)
            if os.environ.get('FIREWALL_OPEN_OUTCOME', '') not in ('', 'success'):
                print('::warning::SSH closure checked; uncertain open marker retained for recovery.')
            else:
                print('Temporary SSH cleanup verified.')
        else:
            raise ValueError('expected open, close or recover')
        return 0
    except (OSError, ValueError, KeyError, IndexError, subprocess.SubprocessError) as error:
        # Never emit AWS command output or credentials.
        print(f'::error::Temporary SSH firewall operation failed ({type(error).__name__}). '
              'Check the kairos-deploy-ssh instance tag and recover before retrying.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())

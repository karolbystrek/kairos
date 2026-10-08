"""Temporary access must never remove administrator or HTTPS access."""
import copy
import json
import os
import subprocess
import unittest
from unittest.mock import patch

import ssh_firewall


class FakeAWS:
    def __init__(self):
        self.tag = None
        self.ports = [
            {'fromPort': 22, 'toPort': 22, 'protocol': 'tcp',
             'cidrs': ['89.67.6.243/32'], 'ipv6Cidrs': [],
             'cidrListAliases': ['lightsail-connect']},
            {'fromPort': 443, 'toPort': 443, 'protocol': 'tcp',
             'cidrs': ['173.245.48.0/20'], 'ipv6Cidrs': [], 'cidrListAliases': []}]
        self.fail_open = self.fail_close = False
        self.pending_open = False

    def __call__(self, action, **options):
        assert options.get('instance_name', options.get('resource_name')) == 'kairos-production'
        if action == 'get-instance':
            return {'instance': {'publicIpAddress': '52.58.250.75', 'ipv6Addresses': [],
                    'tags': [] if self.tag is None else [{'key': 'kairos-deploy-ssh', 'value': self.tag}]}}
        if action == 'get-operations-for-resource':
            return {'operations': [{'operationType': 'OpenInstancePublicPorts',
                                    'isTerminal': not self.pending_open}]}
        if action == 'get-instance-port-states':
            return {'portStates': copy.deepcopy(self.ports)}
        if action == 'tag-resource':
            self.tag = json.loads(options['tags'])[0]['value']
        elif action == 'untag-resource':
            self.tag = None
        elif action in ('open-instance-public-ports', 'close-instance-public-ports'):
            port = json.loads(options['port_info'])
            assert port['fromPort'] == port['toPort'] == 22 and port['protocol'] == 'tcp'
            cidr = port['cidrs'][0]
            if action == 'open-instance-public-ports':
                if self.fail_open:
                    raise OSError('AWS rejected open')
                self.ports[0]['cidrs'].append(cidr)
            else:
                if self.fail_close:
                    raise OSError('AWS rejected close')
                if cidr in self.ports[0]['cidrs']:
                    self.ports[0]['cidrs'].remove(cidr)
        else:
            raise AssertionError(action)
        return {}


class FirewallTests(unittest.TestCase):
    def setUp(self):
        self.aws = FakeAWS()
        self.original = copy.deepcopy(self.aws.ports)
        self.patch = patch('ssh_firewall.aws', self.aws)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.sleep = patch('ssh_firewall.time.sleep')
        self.sleep.start()
        self.addCleanup(self.sleep.stop)
        self.firewall = ssh_firewall.Firewall('kairos-production', '89.67.6.243/32',
                                             '123-1', '52.58.250.75')

    def test_success_or_failed_deployment_cleanup_preserves_permanent_rules(self):
        self.firewall.open('8.8.8.8')
        self.assertIn('8.8.8.8/32', self.aws.ports[0]['cidrs'])
        self.firewall.cleanup()
        self.assertEqual(self.aws.ports, self.original)
        self.assertIsNone(self.aws.tag)

    def test_interrupted_run_is_recovered_before_new_access(self):
        self.aws.tag = '122-1:1.1.1.1/32'
        self.aws.ports[0]['cidrs'].append('1.1.1.1/32')
        self.firewall.open('8.8.8.8')
        self.assertNotIn('1.1.1.1/32', self.aws.ports[0]['cidrs'])
        self.assertEqual(self.aws.tag, '123-1:8.8.8.8/32')
        self.firewall.cleanup()
        self.assertEqual(self.aws.ports, self.original)

    def test_failed_open_retains_marker_and_can_be_cleaned(self):
        self.aws.fail_open = True
        with self.assertRaises(OSError):
            self.firewall.open('8.8.8.8')
        self.assertEqual(self.aws.tag, '123-1:8.8.8.8/32')
        self.firewall.cleanup()
        self.assertIsNone(self.aws.tag)
        self.assertEqual(self.aws.ports, self.original)

    def test_failed_close_keeps_marker_for_next_run(self):
        self.firewall.open('8.8.8.8')
        self.aws.fail_close = True
        with self.assertRaises(OSError):
            self.firewall.cleanup()
        self.assertEqual(self.aws.tag, '123-1:8.8.8.8/32')
        self.assertIn('8.8.8.8/32', self.aws.ports[0]['cidrs'])

    def test_pending_open_never_discards_recovery_marker(self):
        self.aws.tag = '123-1:8.8.8.8/32'
        self.aws.pending_open = True
        with self.assertRaises(TimeoutError):
            self.firewall.cleanup()
        self.assertEqual(self.aws.tag, '123-1:8.8.8.8/32')
        # The delayed open becomes visible later; the next run must still recover it.
        self.aws.pending_open = False
        self.aws.ports[0]['cidrs'].append('8.8.8.8/32')
        self.firewall.cleanup(recover=True)
        self.assertEqual(self.aws.ports, self.original)
        self.assertIsNone(self.aws.tag)

    def test_uncertain_open_keeps_marker_even_when_close_succeeds(self):
        self.firewall.open('8.8.8.8')
        self.firewall.cleanup(keep_marker=True)
        self.assertEqual(self.aws.ports, self.original)
        self.assertEqual(self.aws.tag, '123-1:8.8.8.8/32')

    def test_cleanup_refuses_another_runs_rule(self):
        self.aws.tag = '124-1:8.8.8.8/32'
        with self.assertRaises(ValueError):
            self.firewall.cleanup()
        self.assertEqual(self.aws.tag, '124-1:8.8.8.8/32')

    def test_invalid_or_admin_addresses_cannot_be_opened_or_cleaned(self):
        for address in ('127.0.0.1', '0.0.0.0', '::1', '89.67.6.243', '8.8.8.8/24'):
            with self.subTest(address=address), self.assertRaises(ValueError):
                self.firewall.open(address)
        self.aws.tag = '122-1:89.67.6.243/32'
        with self.assertRaises(ValueError):
            self.firewall.open('8.8.8.8')
        self.assertEqual(self.aws.ports, self.original)

    def test_unmarked_existing_rule_is_not_adopted(self):
        self.aws.ports[0]['cidrs'].append('8.8.8.8/32')
        with self.assertRaises(ValueError):
            self.firewall.open('8.8.8.8')
        self.assertIsNone(self.aws.tag)

    def test_wrong_host_or_missing_admin_configuration_fails(self):
        with self.assertRaises(ValueError):
            ssh_firewall.Firewall('kairos-production', '', '123-1', '52.58.250.75')
        self.firewall.host = '52.58.250.76'
        with self.assertRaises(ValueError):
            self.firewall.open('8.8.8.8')
        self.assertEqual(self.aws.ports, self.original)


class AWSOperationTests(unittest.TestCase):
    def test_mutation_waits_for_accepted_operation_to_complete(self):
        accepted = {'operation': {'id': 'op-1', 'isTerminal': False, 'status': 'Started'}}
        completed = {'operation': {'id': 'op-1', 'isTerminal': True, 'status': 'Succeeded'}}
        responses = [subprocess.CompletedProcess([], 0, json.dumps(value), '')
                     for value in (accepted, completed)]
        with patch.dict(os.environ, {'AWS_REGION': 'eu-central-1'}), \
                patch('ssh_firewall.subprocess.run', side_effect=responses) as command, \
                patch('ssh_firewall.time.sleep'):
            ssh_firewall.aws('open-instance-public-ports', instance_name='kairos-production',
                             port_info='{}')
        self.assertEqual(command.call_args_list[1].args[0][2], 'get-operation')
        self.assertIn('op-1', command.call_args_list[1].args[0])

    def test_failed_accepted_operation_is_not_reported_as_success(self):
        failed = {'operation': {'id': 'op-1', 'isTerminal': True, 'status': 'Failed'}}
        with patch.dict(os.environ, {'AWS_REGION': 'eu-central-1'}), \
                patch('ssh_firewall.subprocess.run', return_value=
                      subprocess.CompletedProcess([], 0, json.dumps(failed), '')):
            with self.assertRaises(OSError):
                ssh_firewall.aws('open-instance-public-ports', instance_name='kairos-production',
                                 port_info='{}')


if __name__ == '__main__':
    unittest.main()

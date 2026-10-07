"""Run real gateway checks in disposable containers; never use the Kairos stack.

Run: python3 tests/gateway-test.py (requires Docker and OpenSSL).
Catches missing throttling, spoofable client identity, routing leaks and TLS/port
regressions in the local and hosted Compose overlays.
"""
import concurrent.futures
import ipaddress
import json
from pathlib import Path
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
IMAGE = 'nginx:1.28-alpine'


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT).strip()


def main():
    name = 'kairos-gateway-test-' + uuid.uuid4().hex[:12]
    containers = []
    with tempfile.TemporaryDirectory(prefix=name) as directory:
        work = Path(directory)
        env = work / 'test.env'
        env.write_text((ROOT / '.env.example').read_text() + '\n' + '\n'.join([
            'KAIROS_IMAGE_REGISTRY=example.invalid/kairos',
            'KAIROS_RELEASE_VERSION=test',
            f'KAIROS_APPLICATION_SECRETS_DIRECTORY={work}',
            f'KAIROS_TLS_DIRECTORY={work}',
        ]) + '\n')
        run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
            '-keyout', str(work / 'tls.key'), '-out', str(work / 'tls.crt'),
            '-days', '1', '-subj', '/CN=api.kairos.localhost')
        backend = work / 'backend.conf'
        backend.write_text('''server {
    listen 3000;
    listen 8080;
    location / {
        default_type text/plain;
        return 200 "$server_port|$http_x_real_ip|$http_x_forwarded_for|$http_x_forwarded_proto|$http_x_forwarded_port|$http_forwarded|$http_cf_connecting_ip|$http_host";
    }
}
''')
        run('docker', 'network', 'create', name)
        try:
            containers.append(name + '-backend')
            run('docker', 'run', '-d', '--name', containers[-1], '--network', name,
                '--network-alias', 'api', '--network-alias', 'customer-app',
                '--network-alias', 'panel-app', '-v', f'{backend}:/etc/nginx/conf.d/default.conf:ro', IMAGE)
            for mode in ('local', 'deployment'):
                compose = ('docker', 'compose', '--env-file', str(env), '-f', str(ROOT / 'compose.yaml'),
                           '-f', str(ROOT / f'compose.{mode}.yaml'))
                run(*compose, 'config', '--quiet')
                config = json.loads(run(*compose, 'config', '--format', 'json'))
                nginx = config['services']['nginx']
                ports = nginx['ports']
                assert len(ports) == 1 and ports[0]['target'] == 443, ports
                assert ports[0].get('host_ip', '') == ('127.0.0.1' if mode == 'local' else ''), ports
                assert all(not service.get('ports') for key, service in config['services'].items() if key != 'nginx')
                assert set(config['networks']) == {'gateway', 'data'}
                assert config['networks']['data']['internal']
                tls = next(v for v in nginx['volumes'] if v['target'] == '/run/secrets/tls')
                assert tls['read_only'] and not tls['bind']['create_host_path']
                if mode == 'deployment':
                    assert tls['source'] == str(work)
                trust = work / 'cloudflare-real-ip.conf'
                source = ROOT / 'nginx/cloudflare-real-ip.conf'
                trust.write_text(source.read_text())
                gateway = name + '-' + mode
                containers.append(gateway)
                args = ['docker', 'run', '-d', '--name', gateway, '--network', name,
                        '-p', '127.0.0.1::443',
                        '-v', f'{ROOT / "nginx/default.conf.template"}:/etc/nginx/templates/default.conf.template:ro',
                        '-v', f'{work}:/run/secrets/tls:ro',
                        '-v', f'{trust}:/etc/nginx/cloudflare-real-ip.conf:ro']
                for key, value in nginx['environment'].items():
                    args += ['-e', f'{key}={value}']
                run(*args, IMAGE)
                port = run('docker', 'port', gateway, '443/tcp').rsplit(':', 1)[1]
                context = ssl._create_unverified_context()

                def request(path, host='api.kairos.localhost', client='203.0.113.1', method='GET'):
                    headers = {'Host': host, 'CF-Connecting-IP': client,
                               'X-Forwarded-For': '198.51.100.10', 'Forwarded': 'for=198.51.100.10'}
                    req = urllib.request.Request(f'https://127.0.0.1:{port}{path}', headers=headers, method=method)
                    try:
                        with urllib.request.urlopen(req, context=context, timeout=5) as response:
                            return response.status, response.read().decode(), response.headers
                    except urllib.error.HTTPError as error:
                        return error.code, '', error.headers

                for attempt in range(40):
                    try:
                        if request('/api/auth/v1/csrf')[0] == 200:
                            break
                    except (OSError, urllib.error.URLError):
                        time.sleep(0.1)
                else:
                    raise AssertionError('gateway did not start')
                run('docker', 'exec', gateway, 'nginx', '-t')
                status, body, _ = request('/api/auth/v1/csrf')
                fields = body.split('|')
                peer = fields[1]
                ipaddress.ip_address(peer)
                assert status == 200 and fields == ['8080', peer, peer, 'https', '443', '', '', 'api.kairos.localhost'], body
                assert request('/', 'customer.kairos.localhost')[1].startswith('3000|')
                assert request('/', 'panel.kairos.localhost')[1].startswith('3000|')
                for path in ('/api', '/api/orders/v1'):
                    assert request(path, 'customer.kairos.localhost')[0] == 404
                    assert request(path, 'panel.kairos.localhost')[0] == 404
                for path in ('/', '/health', '/actuator/health', '/api/actuator', '/api/actuator/health'):
                    assert request(path)[0] == 404, path
                try:
                    request('/', 'unknown.invalid')
                    raise AssertionError('unknown host accepted')
                except (OSError, urllib.error.URLError):
                    pass
                protected = ['/api/auth/v1/login', '/api/auth/v1/password',
                             '/api/tenant-registrations/v1', '/api/account-invitation-redemptions/v1']
                statuses = [request(protected[i % 4], client=f'203.0.113.{i + 1}', method='POST')[0] for i in range(9)]
                assert statuses[:6] == [200] * 6 and statuses[6:] == [429] * 3, (mode, statuses)
                for path, method in (
                    ('/api/auth/v1/csrf', 'GET'), ('/api/auth/v1/me', 'GET'),
                    ('/api/orders/v1', 'GET'), ('/api/orders/v1', 'POST'),
                    ('/api/auth/v1/login', 'GET'), ('/api/auth/v1/login', 'OPTIONS'),
                    ('/api/auth/v1/logout', 'POST'),
                    ('/api/account-invitation-previews/v1', 'POST'),
                ):
                    assert request(path, method=method)[0] == 200, (path, method)
                status, _, headers = request('/api/tracked-orders/v1/test/events')
                assert status == 200 and headers['X-Accel-Buffering'] == 'no'
                with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
                    external = list(pool.map(lambda _: request('/api/external/orders/v1')[0], range(60)))
                assert 200 in external and 429 in external and set(external) == {200, 429}, external
                if mode == 'deployment':
                    # Trust only this disposable fixture's actual peer for the positive case.
                    with trust.open('a') as file:
                        file.write(f'\nset_real_ip_from {peer};\n')
                    run('docker', 'exec', gateway, 'nginx', '-t')
                    run('docker', 'exec', gateway, 'nginx', '-s', 'reload')
                    time.sleep(0.3)
                    assert request('/api/auth/v1/csrf', client='2001:db8::1')[1].split('|')[1:3] == ['2001:db8::1'] * 2
                    for i, path in enumerate(protected):
                        client = f'203.0.113.{100 + i}'
                        statuses = [request(path, client=client, method='POST')[0] for _ in range(7)]
                        assert statuses == [200] * 6 + [429], statuses
                    assert request('/api/auth/v1/csrf', client='203.0.113.100')[0] == 200
                print(f'{mode}: Compose, TLS, routing, header trust, throttling and SSE checks passed')
                run('docker', 'rm', '-f', gateway)
                containers.remove(gateway)
        finally:
            for container in reversed(containers):
                subprocess.run(['docker', 'rm', '-f', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            run('docker', 'network', 'rm', name)


if __name__ == '__main__':
    main()

"""Transfer failures must never activate or deploy an incomplete release."""
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

from release_manifest import RUNTIME_FILES

SCRIPT = Path(__file__).with_name('upload_release.py')
REVISION = 'a' * 40


class UploadTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.artifact = self.root / 'artifact'
        self.artifact.mkdir()
        self.remote = self.root / 'remote'
        self.remote.mkdir()
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.manifest = {'revision': REVISION, 'images': {
            service: f'ghcr.io/karolbystrek/kairos/{service}@sha256:' + 'b' * 64
            for service in ('customer-app', 'panel-app', 'api')}}
        self.bundle()
        fake = '''#!/usr/bin/env python3
import json, os, pathlib, subprocess, sys
root = pathlib.Path(os.environ['FAKE_ROOT'])
with (root / 'calls').open('a') as log:
    log.write(json.dumps([pathlib.Path(sys.argv[0]).name, *sys.argv[1:]]) + '\\n')
args = sys.argv[1:]
if os.environ.get('FAIL_SSH'): sys.exit(255)
for option in ('StrictHostKeyChecking=yes', 'BatchMode=yes', 'IdentitiesOnly=yes'):
    assert option in args
key = pathlib.Path(args[args.index('-i') + 1])
assert key.stat().st_mode & 0o777 == 0o600
assert key.parent.stat().st_mode & 0o777 == 0o700
known = pathlib.Path(next(arg.split('=', 1)[1] for arg in args if arg.startswith('UserKnownHostsFile=')))
assert known.read_text() == 'verified-host-key\\n'
assert known.stat().st_mode & 0o777 == 0o600
if pathlib.Path(sys.argv[0]).name == 'scp':
    if os.environ.get('FAIL_TRANSFER'): sys.exit(1)
    import shutil
    shutil.copyfile(args[-2], args[-1].split(':', 1)[1].replace('/srv/kairos', str(root / 'remote')))
else:
    command = args[-1].replace('/srv/kairos', str(root / 'remote'))
    command = command.replace('mv -T ', 'mv ')
    script = sys.stdin.read().replace('mv -T ', 'mv ').replace('/srv/kairos', str(root / 'remote'))
    result = subprocess.run(['bash', '-c', command], input=script, text=True, capture_output=True)
    print(result.stdout.replace(str(root / 'remote'), '/srv/kairos'), end='')
    print(result.stderr, end='', file=sys.stderr)
    sys.exit(result.returncode)
'''
        for name in ('ssh', 'scp'):
            path = self.bin / name
            path.write_text(fake)
            path.chmod(0o755)
        self.env = {**os.environ, 'PATH': str(self.bin) + os.pathsep + os.environ['PATH'],
                    'FAKE_ROOT': str(self.root), 'DEPLOY_HOST': 'host.example',
                    'DEPLOY_USER': 'deployer', 'DEPLOY_SSH_KEY': 'private-key',
                    'DEPLOY_KNOWN_HOSTS': 'verified-host-key\n',
                    'GITHUB_STEP_SUMMARY': str(self.root / 'summary')}

    def bundle(self):
        (self.artifact / 'release.json').write_text(json.dumps(self.manifest))
        with tarfile.open(self.artifact / 'release.tar.gz', 'w:gz') as archive:
            archive.add(self.artifact / 'release.json', arcname='release.json')
            for name in RUNTIME_FILES:
                path = self.root / 'source' / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('echo "$@" > "$(dirname "$1")/invoked"\nexit "${FAIL_DEPLOY:-0}"\n'
                                if name == 'deployment/deploy.sh' else '# fixture\n')
                archive.add(path, arcname=name)

    def run_upload(self):
        return subprocess.run(['python3', str(SCRIPT), str(self.artifact), REVISION],
                              env=self.env, capture_output=True, text=True)

    def test_complete_release_and_retry(self):
        for _ in range(2):
            result = self.run_upload()
            self.assertEqual(result.returncode, 0, result.stderr)
        release = self.remote / 'releases' / REVISION
        self.assertEqual(json.loads((release / 'release.json').read_text()), self.manifest)
        self.assertEqual((release.parent / 'invoked').read_text().strip(),
                         f'{release} {self.remote}/production.env')
        self.assertEqual(list(release.parent.glob('.incoming-*')), [])
        self.assertIn(self.manifest['images']['api'], (self.root / 'summary').read_text())
        calls = [json.loads(line) for line in (self.root / 'calls').read_text().splitlines()]
        self.assertTrue(all(not Path(call[call.index('-i') + 1]).exists() for call in calls))

    def test_failed_transfer_does_not_activate(self):
        self.env['FAIL_TRANSFER'] = '1'
        self.assertNotEqual(self.run_upload().returncode, 0)
        self.assertFalse((self.remote / 'releases' / REVISION).exists())
        self.assertFalse((self.remote / 'releases' / 'invoked').exists())
        self.assertEqual(list((self.remote / 'releases').glob('.incoming-*')), [])

    def test_deployment_failure_is_propagated(self):
        self.env['FAIL_DEPLOY'] = '1'
        self.assertNotEqual(self.run_upload().returncode, 0)

    def test_ssh_failure_never_transfers(self):
        self.env['FAIL_SSH'] = '1'
        result = self.run_upload()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('remote staging', result.stderr)
        calls = [json.loads(line) for line in (self.root / 'calls').read_text().splitlines()]
        self.assertEqual([call[0] for call in calls], ['ssh'])

    def test_changed_retry_preserves_existing_release(self):
        self.assertEqual(self.run_upload().returncode, 0)
        original = (self.remote / 'releases' / REVISION / 'release.json').read_bytes()
        (self.remote / 'releases' / 'invoked').unlink()
        self.manifest['images']['api'] = 'ghcr.io/karolbystrek/kairos/api@sha256:' + 'c' * 64
        self.bundle()
        result = self.run_upload()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.remote / 'releases' / REVISION / 'release.json').read_bytes(), original)
        self.assertFalse((self.remote / 'releases' / 'invoked').exists())
        self.assertEqual(list((self.remote / 'releases').glob('.incoming-*')), [])

    def test_manifest_outside_archive_must_match(self):
        self.manifest['images']['api'] = 'ghcr.io/karolbystrek/kairos/api@sha256:' + 'c' * 64
        (self.artifact / 'release.json').write_text(json.dumps(self.manifest))
        result = self.run_upload()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('release validation', result.stderr)
        self.assertFalse((self.root / 'calls').exists())

    def test_invalid_inputs_never_connect(self):
        for key, value in (('DEPLOY_HOST', 'host;touch /tmp/unsafe'), ('DEPLOY_USER', '-option'),
                           ('DEPLOY_SSH_KEY', ''), ('DEPLOY_KNOWN_HOSTS', '')):
            with self.subTest(key=key):
                previous = self.env[key]
                self.env[key] = value
                self.assertNotEqual(self.run_upload().returncode, 0)
                self.env[key] = previous
        self.manifest['revision'] = 'c' * 40
        self.bundle()
        self.assertNotEqual(self.run_upload().returncode, 0)
        self.assertFalse((self.root / 'calls').exists())


if __name__ == '__main__':
    unittest.main()

"""Transfer this run's verified bundle over SSH and invoke the host deployer."""
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tarfile
import tempfile

from deploy import inputs, failure_reason, ValidationError
from release_manifest import RUNTIME_FILES


def main():
    stage = 'release validation'
    try:
        artifact, revision = Path(sys.argv[1]), sys.argv[2]
        if not re.fullmatch(r'[0-9a-f]{40}', revision):
            raise ValidationError('invalid workflow revision')
        with tempfile.TemporaryDirectory(prefix='kairos-actions-') as directory:
            scratch = Path(directory)
            scratch.chmod(0o700)
            extracted = scratch / 'release'
            extracted.mkdir()
            with tarfile.open(artifact / 'release.tar.gz', 'r:gz') as archive:
                members = archive.getmembers()
                if (len(members) != len(RUNTIME_FILES) + 1
                        or {member.name for member in members} != {'release.json', *RUNTIME_FILES}
                        or any(not member.isfile() for member in members)):
                    raise ValidationError('invalid release archive contents')
                archive.extractall(extracted, filter='data')
            placeholder = scratch / 'external.env'
            placeholder.touch()
            _, _, manifest = inputs([str(extracted), str(placeholder)])
            if (manifest['revision'] != revision
                    or manifest != json.loads((artifact / 'release.json').read_text())):
                raise ValidationError('artifact must match the workflow revision and bundled manifest')
            with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
                summary.write(f'## Production release\n\nRevision: `{revision}`\n\n')
                for service, image in manifest['images'].items():
                    summary.write(f'- {service}: `{image}`\n')

            stage = 'SSH configuration'
            host, user = os.environ['DEPLOY_HOST'], os.environ['DEPLOY_USER']
            if (not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]*', host)
                    or not re.fullmatch(r'[a-z_][a-z0-9_-]*', user)):
                raise ValidationError('invalid deployment host or user')
            for variable, filename in (('DEPLOY_SSH_KEY', 'identity'), ('DEPLOY_KNOWN_HOSTS', 'known_hosts')):
                value = os.environ[variable]
                if not value.strip():
                    raise ValidationError('SSH key and verified known hosts are required')
                path = scratch / filename
                path.touch(mode=0o600)
                path.write_text(value.rstrip('\n') + '\n')
            options = ['-F', '/dev/null', '-i', str(scratch / 'identity'),
                       '-o', 'StrictHostKeyChecking=yes', '-o', 'BatchMode=yes',
                       '-o', 'IdentitiesOnly=yes', '-o', 'ConnectTimeout=15',
                       '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=3',
                       '-o', f'UserKnownHostsFile={scratch / "known_hosts"}']
            destination = f'{user}@{host}'

            def ssh(command, **kwargs):
                return subprocess.run(['ssh', *options, destination, command],
                                      check=True, text=True, stderr=subprocess.DEVNULL, **kwargs)

            stage = 'remote staging'
            incoming = ssh('umask 077; mkdir -p /srv/kairos/releases && '
                           'mktemp -d /srv/kairos/releases/.incoming-XXXXXXXXXX',
                           stdout=subprocess.PIPE, timeout=60).stdout.strip()
            if not re.fullmatch(r'/srv/kairos/releases/\.incoming-[A-Za-z0-9]{10}', incoming):
                raise ValidationError('invalid remote staging path')
            try:
                stage = 'bundle transfer'
                subprocess.run(['scp', *options, str(artifact / 'release.tar.gz'),
                                f'{destination}:{incoming}/release.tar.gz'], check=True,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=300)
                stage = 'activation and deployment'
                ssh(f'bash -s -- {incoming} {revision}', input='''set -euo pipefail
umask 077
incoming=$1
release=/srv/kairos/releases/$2
if [[ -e "$release" ]]; then
  cmp --silent "$incoming/release.tar.gz" "$release/release.tar.gz"
else
  tar -xzf "$incoming/release.tar.gz" -C "$incoming"
  mv -T "$incoming" "$release"
fi
bash "$release/deployment/deploy.sh" "$release" /srv/kairos/production.env
''', timeout=3600)
            finally:
                # Only a validated temporary path is removed; activated releases persist.
                try:
                    ssh(f'rm -rf -- {shlex.quote(incoming)}', stdout=subprocess.DEVNULL, timeout=60)
                except subprocess.SubprocessError:
                    print('Remote staging cleanup failed.', file=sys.stderr)
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, tarfile.TarError,
            subprocess.SubprocessError) as error:
        print(f'Release upload failed at {stage}: {failure_reason(error)}.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())

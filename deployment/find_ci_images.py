"""Find validated main CI images for the deployment workflow revision."""
import json
import os
import re
import subprocess
import sys


def github_api(endpoint, paginate=False):
    command = ['gh', 'api', endpoint]
    if paginate:
        command += ['--paginate', '--slurp']
    return json.loads(subprocess.check_output(command, text=True))


def find_ci_images(repository, sha, api=github_api):
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('revision must be a full lowercase commit SHA')
    root = f'repos/{repository}'
    pages = api(f'{root}/actions/workflows/ci.yml/runs?branch=main&head_sha={sha}&status=success&per_page=100', True)
    for page in pages:
        for run in page['workflow_runs']:
            if (run.get('head_sha') != sha or run.get('head_branch') != 'main'
                    or run.get('conclusion') != 'success'
                    or run.get('event') not in ('push', 'workflow_dispatch')):
                continue
            artifacts = api(f'{root}/actions/runs/{run["id"]}/artifacts?per_page=100')['artifacts']
            available = {artifact['name'] for artifact in artifacts if not artifact['expired']}
            if {'image-customer-app', 'image-panel-app', 'image-api'} <= available:
                return run['id']
    raise ValueError('No successful main CI run with all image artifacts; finish or rerun CI for this commit')


if __name__ == '__main__':
    try:
        sha = os.environ['GITHUB_SHA']
        run_id = find_ci_images(os.environ['GITHUB_REPOSITORY'], sha)
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f'run_id={run_id}\n')
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(f'Deployment revision: `{sha}`\n\nValidated CI images: https://github.com/{os.environ["GITHUB_REPOSITORY"]}/actions/runs/{run_id}\n')
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f'CI image lookup failed: {error}', file=sys.stderr)
        sys.exit(1)

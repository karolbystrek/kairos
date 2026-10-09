"""Resolve a published main release before requesting production approval."""
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


def select_release(repository, revision, api=github_api):
    revision = revision.strip()
    if revision and not re.fullmatch(r'[0-9a-fA-F]{7,40}', revision):
        raise ValueError('revision must be a commit SHA (7–40 hexadecimal characters)')
    root = f'repos/{repository}'
    sha = api(f'{root}/commits/{revision or "main"}')['sha']
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('GitHub returned an invalid revision')
    if api(f'{root}/compare/{sha}...main')['status'] not in ('ahead', 'identical'):
        raise ValueError('selected commit must belong to main history')
    pages = api(f'{root}/actions/workflows/ci.yml/runs?branch=main&head_sha={sha}&status=success&per_page=100', True)
    for page in pages:
        for run in page['workflow_runs']:
            if (run.get('head_sha') != sha or run.get('head_branch') != 'main'
                    or run.get('conclusion') != 'success'
                    or run.get('event') not in ('push', 'workflow_dispatch')):
                continue
            artifacts = api(f'{root}/actions/runs/{run["id"]}/artifacts?per_page=100')['artifacts']
            if any(artifact['name'] == 'release-manifest' and not artifact['expired']
                   for artifact in artifacts):
                return sha, run['id']
    raise ValueError('No successful CI release with an available artifact; finish or rerun CI for this commit')


if __name__ == '__main__':
    try:
        sha, run_id = select_release(os.environ['GITHUB_REPOSITORY'], os.environ.get('REQUESTED_REVISION', ''))
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f'revision={sha}\nrun_id={run_id}\n')
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(f'Selected revision: `{sha}`\n\nCI release: https://github.com/{os.environ["GITHUB_REPOSITORY"]}/actions/runs/{run_id}\n')
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f'Release selection failed: {error}', file=sys.stderr)
        sys.exit(1)

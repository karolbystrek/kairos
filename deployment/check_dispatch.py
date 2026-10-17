"""Reject superseded manual deployments before granting any cloud access."""
import json
import os
import subprocess
import sys


def main():
    try:
        run_id = int(os.environ['GITHUB_RUN_ID'])
        repository = os.environ['GITHUB_REPOSITORY']
        result = subprocess.run([
            'gh', 'api', f'repos/{repository}/actions/workflows/deploy.yml/runs',
            '--method', 'GET', '-f', 'event=workflow_dispatch', '-f', 'branch=main',
            '-f', 'per_page=1',
        ], check=True, capture_output=True, text=True, timeout=30)
        runs = json.loads(result.stdout)['workflow_runs']
        if not runs or runs[0]['id'] != run_id:
            print('Deployment superseded by a newer manual dispatch.', file=sys.stderr)
            return 1
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        print('Could not verify the latest deployment dispatch.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())

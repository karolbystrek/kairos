"""Exercise the guard across a fake GitHub CLI boundary."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class DispatchTest(unittest.TestCase):
    def test_only_latest_dispatch_proceeds_and_api_failure_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            gh = Path(directory) / 'gh'
            gh.write_text('#!' + sys.executable + '\nimport os, sys\nprint(os.environ["RESPONSE"])\nsys.exit(int(os.environ["STATUS"]))\n')
            gh.chmod(0o755)
            for response, status, expected in (
                ({'workflow_runs': [{'id': 12}]}, 0, 0),
                ({'workflow_runs': [{'id': 13}]}, 0, 1),
                ({'workflow_runs': []}, 0, 1),
                ({'workflow_runs': [{'id': 12}]}, 1, 1),
            ):
                with self.subTest(response=response, status=status):
                    result = subprocess.run([sys.executable, str(Path(__file__).with_name('check_dispatch.py'))],
                        env={**os.environ, 'PATH': directory + os.pathsep + os.environ['PATH'],
                             'GITHUB_RUN_ID': '12', 'GITHUB_REPOSITORY': 'karolbystrek/kairos',
                             'RESPONSE': json.dumps(response), 'STATUS': str(status)}, capture_output=True, text=True)
                    self.assertEqual(result.returncode, expected, result.stderr)


if __name__ == '__main__':
    unittest.main()

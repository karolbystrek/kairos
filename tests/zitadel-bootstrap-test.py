"""Offline check for provider bootstrap: no containers or network requests."""
import contextlib
import io
import json
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'deployment/zitadel/bootstrap.py'

class BootstrapTest(unittest.TestCase):
    def test_provisions_standard_roles_and_keeps_admin_token_out_of_api_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            admin = root / 'admin.pat'
            admin.write_text('test-admin')
            api = root / 'kairos.pat'
            calls = []

            def resolve(value):
                return api if value == '/credentials/kairos.pat' else admin

            def respond(request, timeout):
                calls.append(request)
                value = {'org': {'id': 'org'}} if request.full_url.endswith('/orgs/me') else {}
                if request.full_url.endswith('/pats'):
                    value = {'token': 'test-api'}
                return io.BytesIO(json.dumps(value).encode())

            with patch('pathlib.Path', side_effect=resolve), patch('urllib.request.urlopen', side_effect=respond), contextlib.redirect_stdout(io.StringIO()):
                runpy.run_path(str(SCRIPT))
            pat_request = next(call for call in calls if call.full_url.endswith('/pats'))
            self.assertEqual(json.loads(pat_request.data), {'expirationDate': '9999-12-31T23:59:59Z'})
            self.assertEqual(api.read_text(), 'test-api')
            self.assertEqual(api.stat().st_mode & 0o777, 0o600)
            payloads = [json.loads(call.data) for call in calls if call.data]
            self.assertIn({'userId': 'kairos-api', 'roles': ['ORG_USER_MANAGER']}, payloads)
            self.assertIn({'userId': 'kairos-api', 'roles': ['IAM_LOGIN_CLIENT']}, payloads)
            self.assertNotIn('IAM_OWNER', json.dumps(payloads))

    def test_restart_preserves_credentials_without_provisioning_again(self):
        with tempfile.TemporaryDirectory() as directory:
            api = Path(directory) / 'kairos.pat'
            api.write_text('existing-api')
            with patch('pathlib.Path', return_value=api), patch('urllib.request.urlopen') as http, contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit) as result:
                    runpy.run_path(str(SCRIPT))
            self.assertEqual(result.exception.code, 0)
            http.assert_not_called()
            self.assertEqual(api.read_text(), 'existing-api')

if __name__ == '__main__':
    unittest.main()

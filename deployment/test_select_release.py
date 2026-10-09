"""Only successfully published main revisions may reach production approval."""
import unittest
from unittest.mock import Mock

from select_release import select_release

SHA = 'a' * 40


class SelectionTests(unittest.TestCase):
    def test_default_and_explicit_revision_select_available_release(self):
        for revision in ('', SHA[:8]):
            with self.subTest(revision=revision):
                api = Mock(side_effect=[{'sha': SHA}, {'status': 'ahead'},
                    [{'workflow_runs': [{'id': 9, 'head_sha': SHA, 'head_branch': 'main',
                        'event': 'push', 'conclusion': 'success'}]}],
                    {'artifacts': [{'name': 'release-manifest', 'expired': False}]}])
                self.assertEqual(select_release('owner/repo', revision, api), (SHA, 9))
                self.assertTrue(api.call_args_list[0].args[0].endswith('/commits/' + (revision or 'main')))

    def test_rejects_non_commit_input_before_api(self):
        api = Mock()
        with self.assertRaises(ValueError):
            select_release('owner/repo', 'feature-branch', api)
        api.assert_not_called()

    def test_rejects_revision_outside_main(self):
        api = Mock(side_effect=[{'sha': SHA}, {'status': 'diverged'}])
        with self.assertRaises(ValueError):
            select_release('owner/repo', SHA, api)

    def test_rejects_failed_unpublished_and_expired_releases(self):
        for runs, artifacts in [([], []),
                ([{'id': 9, 'conclusion': 'failure'}], []),
                ([{'id': 9, 'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'conclusion': 'success'}], []),
                ([{'id': 9, 'head_sha': SHA, 'head_branch': 'main', 'event': 'workflow_dispatch', 'conclusion': 'success'}],
                 [{'name': 'release-manifest', 'expired': True}])]:
            api = Mock(side_effect=[{'sha': SHA}, {'status': 'identical'},
                [{'workflow_runs': runs}], {'artifacts': artifacts}])
            with self.assertRaises(ValueError):
                select_release('owner/repo', SHA, api)

    def test_uses_older_successful_run_when_newest_artifact_is_gone(self):
        run = {'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'conclusion': 'success'}
        api = Mock(side_effect=[{'sha': SHA}, {'status': 'identical'},
            [{'workflow_runs': [dict(run, id=10)]}, {'workflow_runs': [dict(run, id=9)]}],
            {'artifacts': []},
            {'artifacts': [{'name': 'release-manifest', 'expired': False}]}])
        self.assertEqual(select_release('owner/repo', SHA, api), (SHA, 9))

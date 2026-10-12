"""Publication requires all three images from successful CI for the exact main SHA."""
import unittest
from unittest.mock import Mock

from find_ci_images import find_ci_images

SHA = 'a' * 40
RUN = {'id': 9, 'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'conclusion': 'success'}
ARTIFACTS = [{'name': 'image-' + service, 'expired': False}
             for service in ('customer-app', 'panel-app', 'api')]


class ImageLookupTests(unittest.TestCase):
    def test_selects_complete_validated_images(self):
        api = Mock(side_effect=[[{'workflow_runs': [RUN]}], {'artifacts': ARTIFACTS}])
        self.assertEqual(find_ci_images('owner/repo', SHA, api), 9)
        self.assertIn('head_sha=' + SHA, api.call_args_list[0].args[0])

    def test_rejects_invalid_revision_before_api(self):
        api = Mock()
        with self.assertRaises(ValueError):
            find_ci_images('owner/repo', SHA[:8], api)
        api.assert_not_called()

    def test_rejects_ineligible_runs(self):
        for changes in ({'head_sha': 'b' * 40}, {'head_branch': 'feature'},
                        {'conclusion': 'failure'}, {'event': 'pull_request'}):
            api = Mock(return_value=[{'workflow_runs': [dict(RUN, **changes)]}])
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                find_ci_images('owner/repo', SHA, api)

    def test_rejects_missing_or_expired_images(self):
        for artifacts in ([], ARTIFACTS[:2],
                          [dict(item, expired=item['name'] == 'image-api') for item in ARTIFACTS]):
            api = Mock(side_effect=[[{'workflow_runs': [RUN]}], {'artifacts': artifacts}])
            with self.subTest(artifacts=artifacts), self.assertRaises(ValueError):
                find_ci_images('owner/repo', SHA, api)

    def test_falls_back_to_older_successful_run_with_complete_images(self):
        api = Mock(side_effect=[
            [{'workflow_runs': [dict(RUN, id=10)]}, {'workflow_runs': [RUN]}],
            {'artifacts': []}, {'artifacts': ARTIFACTS}])
        self.assertEqual(find_ci_images('owner/repo', SHA, api), 9)

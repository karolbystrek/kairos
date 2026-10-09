"""Producer checks: incorrect or incomplete publication must not yield a bundle."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("release_manifest.py")
REVISION = "a" * 40
SERVICES = ("customer-app", "panel-app", "api")


class ReleaseManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inspections = self.root / "inspections"
        self.inspections.mkdir()
        self.output = self.root / "release"
        self.images = {}
        for index, service in enumerate(SERVICES):
            reference = f"ghcr.io/karolbystrek/kairos/{service}@sha256:{str(index + 1) * 64}"
            self.images[service] = reference
            self.write(service, [{
                "Os": "linux", "Architecture": "amd64", "RepoDigests": [reference],
                "Config": {"Labels": {"org.opencontainers.image.revision": REVISION}},
            }])

    def write(self, service, data):
        (self.inspections / f"{service}.json").write_text(json.dumps(data))

    def run_producer(self, revision=REVISION):
        return subprocess.run(
            [sys.executable, str(SCRIPT), revision, str(self.inspections), str(self.output)],
            capture_output=True, text=True,
        )

    def test_complete_publication_bundles_digest_manifest_and_only_runtime_files(self):
        result = self.run_producer()
        self.assertEqual(result.returncode, 0, result.stderr)
        manifest = json.loads((self.output / "release.json").read_text())
        self.assertEqual(manifest, {"revision": REVISION, "images": self.images})
        with tarfile.open(self.output / "release.tar.gz") as archive:
            self.assertEqual(set(archive.getnames()), {
                "release.json", "compose.yaml", "compose.deployment.yaml",
                "nginx/default.conf.template", "nginx/cloudflare-real-ip.conf",
                "deployment/zitadel/bootstrap.py", "deployment/deploy.sh", "deployment/deploy.py",
                "deployment/postgres/bootstrap.sh",
            })
            self.assertEqual(json.load(archive.extractfile("release.json")), manifest)
            for name in archive.getnames():
                if name != "release.json":
                    self.assertEqual(archive.extractfile(name).read(),
                                     (SCRIPT.parent.parent / name).read_bytes())

    def test_incomplete_publication_creates_no_release(self):
        (self.inspections / "api.json").unlink()
        result = self.run_producer()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("api.json", result.stderr)
        self.assertFalse(self.output.exists())

    def test_unverified_image_creates_no_release(self):
        original = json.loads((self.inspections / "api.json").read_text())
        for field, value in (
            ("Architecture", "arm64"), ("Os", "windows"),
            ("RepoDigests", ["ghcr.io/other/api@sha256:" + "1" * 64]),
            ("RepoDigests", ["ghcr.io/karolbystrek/kairos/api@sha256:bad"]),
            ("Config", {"Labels": {"org.opencontainers.image.revision": "b" * 40}}),
            ("Config", {"Labels": None}),
        ):
            with self.subTest(field=field, value=value):
                image = copy.deepcopy(original)
                image[0][field] = value
                self.write("api", image)
                result = self.run_producer()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("api:", result.stderr)
                self.assertFalse(self.output.exists())

    def test_invalid_revision_creates_no_release(self):
        for revision in ("main", "A" * 40, "a" * 39):
            with self.subTest(revision=revision):
                result = self.run_producer(revision)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("full lowercase Git SHA", result.stderr)
                self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()

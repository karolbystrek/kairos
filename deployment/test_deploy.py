"""Command-boundary checks: no real Docker, network, or operator state is touched."""
import copy
import fcntl
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
REVISION = "a" * 40
SERVICES = ("customer-app", "panel-app", "api")
DOCKER = shutil.which("docker")
RUNTIME_FILES = (
    "compose.yaml", "compose.deployment.yaml", "nginx/default.conf.template",
    "nginx/cloudflare-real-ip.conf", "deployment/zitadel/bootstrap.py",
)


class DeployTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.release = self.root / "release with spaces"
        self.release.mkdir()
        for name in RUNTIME_FILES:
            target = self.release / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / name).read_bytes())
        self.manifest = {"revision": REVISION, "images": {
            service: f"ghcr.io/karolbystrek/kairos/{service}@sha256:" + str(index + 1) * 64
            for index, service in enumerate(SERVICES)
        }}
        self.env_file = self.root / "production.env"
        self.env_file.write_text("POSTGRES_PASSWORD=do-not-print-this-secret\n")
        self.state = self.root / "state"
        self.state.mkdir()
        self.record = self.state / "deployed-release.json"
        self.record.write_text('{"revision":"previous-success"}\n')
        self.original_record = self.record.read_bytes()
        self.secrets = self.root / "secrets"
        self.secrets.mkdir()
        for name in ("webhook-encryption.bin", "vapid-private.pem", "vapid-public.pem",
                     "push-subscription-encryption.bin", "zitadel-masterkey", "tls.crt", "tls.key"):
            (self.secrets / name).write_text("fixture-private-material")
        origins = {
            "NEXT_PUBLIC_CUSTOMER_APP_URL": "https://customer.example.com",
            "PANEL_APP_URL": "https://panel.example.com",
            "NEXT_PUBLIC_API_BASE_URL": "https://api.example.com",
        }
        self.config = {"services": {
            name: {"image": self.manifest["images"].get(name, "fixture:infra"), "environment": {}}
            for name in (*SERVICES, "nginx", "postgres", "redis", "zitadel", "zitadel-bootstrap")
        }}
        self.config["services"]["api"]["environment"] = {
            **origins, "WEBHOOK_DESTINATION_POLICY": "PUBLIC_HTTPS", "PUSH_DESTINATION_POLICY": "PUBLIC_HTTPS",
        }
        self.config["services"]["nginx"]["environment"] = {
            "KAIROS_CUSTOMER_APP_HOST": "customer.example.com", "KAIROS_PANEL_APP_HOST": "panel.example.com",
            "KAIROS_API_HOST": "api.example.com",
        }
        self.config["services"]["api"]["volumes"] = [
            {"type": "bind", "source": str(self.secrets / name), "read_only": True}
            for name in ("webhook-encryption.bin", "vapid-private.pem", "vapid-public.pem",
                         "push-subscription-encryption.bin")
        ]
        self.config["services"]["zitadel"]["volumes"] = [
            {"type": "bind", "source": str(self.secrets / "zitadel-masterkey"), "read_only": True}]
        self.config["services"]["nginx"]["volumes"] = [
            {"type": "bind", "source": str(self.secrets), "target": "/run/secrets/tls", "read_only": True}]
        self.log = self.root / "commands.jsonl"
        self.bin = self.root / "bin"
        self.bin.mkdir()
        fake = f'''#!{sys.executable}
import json, os
from pathlib import Path
import sys, subprocess
name = Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ['COMMAND_LOG'], 'a') as log:
    log.write(json.dumps([name, *args]) + '\\n')
if name == 'docker':
    if 'config' in args:
        if os.environ.get('REAL_COMPOSE'):
            result = subprocess.run([os.environ['REAL_COMPOSE'], *args], capture_output=True, text=True)
            print(result.stdout, end='')
            sys.exit(result.returncode)
        print(Path(os.environ['CONFIG_FIXTURE']).read_text())
    if os.environ.get('FAIL_STAGE') in ('pull', 'redis', 'zitadel', 'api', 'zitadel-bootstrap', 'nginx'):
        stage = os.environ['FAIL_STAGE']
        if (stage == 'pull' and 'pull' in args) or ('up' in args and args[-1] == stage):
            print('do-not-print-this-secret', file=sys.stderr)
            sys.exit(1)
    if 'ps' in args:
        print('api running unhealthy')
else:
    url = args[-1]
    status = os.environ.get('PROBE_STATUS', '200')
    body = os.environ.get('PROBE_BODY', '{{"token":"application-csrf-token"}}')
    Path(args[args.index('--output') + 1]).write_text(body)
    print(status, end='')
    if os.environ.get('FAIL_STAGE') == 'external':
        sys.exit(7)
'''
        for command in ("docker", "curl"):
            path = self.bin / command
            path.write_text(fake)
            path.chmod(0o755)

    def run_deploy(self, manifest=None, release=None, env_file=None, **extra_env):
        (self.release / "release.json").write_text(json.dumps(self.manifest if manifest is None else manifest))
        fixture = self.root / "compose.json"
        fixture.write_text(json.dumps(self.config))
        # Change only the operator-state location and retry sleep in the subprocess;
        # validation, orchestration and external commands are the actual consumer.
        runner = f"""#!{sys.executable}
import os, sys
from pathlib import Path
sys.path.insert(0, str(Path(sys.argv.pop(1)).parent))
import deploy
deploy.STATE_DIRECTORY = Path(os.environ['STATE_FIXTURE'])
deploy.time.sleep = lambda _: None
sys.exit(deploy.main(sys.argv[1:]))
"""
        interpreter = self.bin / "python3"
        interpreter.write_text(runner)
        interpreter.chmod(0o755)
        return subprocess.run(
            ["bash", str(ROOT / "deployment/deploy.sh"),
             str(self.release if release is None else release), str(self.env_file if env_file is None else env_file)],
            env={**os.environ, "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
                 "COMMAND_LOG": str(self.log), "CONFIG_FIXTURE": str(fixture),
                 "STATE_FIXTURE": str(self.state), "PYTHONDONTWRITEBYTECODE": "1", **extra_env},
            capture_output=True, text=True, timeout=15,
        )

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def assert_failed_safely(self, result):
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.record.read_bytes(), self.original_record)
        self.assertNotIn("do-not-print-this-secret", result.stdout + result.stderr)

    def test_invalid_manifest_never_calls_docker(self):
        invalid = []
        for revision in ("main", "A" * 40, "a" * 39, None):
            item = copy.deepcopy(self.manifest)
            item["revision"] = revision
            invalid.append(item)
        for images in (
            {key: value for key, value in self.manifest["images"].items() if key != "api"},
            {**self.manifest["images"], "unknown": "image"},
            {**self.manifest["images"], "api": "ghcr.io/other/api@sha256:" + "1" * 64},
            {**self.manifest["images"], "api": "ghcr.io/karolbystrek/kairos/api@sha256:bad"},
            {**self.manifest["images"], "api": "$(touch /tmp/unsafe)"},
            None,
        ):
            invalid.append({"revision": REVISION, "images": images})
        invalid.extend([[], {**self.manifest, "extra": "unexpected"}])
        for manifest in invalid:
            with self.subTest(manifest=manifest):
                result = self.run_deploy(manifest)
                self.assert_failed_safely(result)
                self.assertEqual(self.commands(), [])

    def test_missing_or_relative_inputs_never_call_docker(self):
        for release, env_file in (("relative", self.env_file), (self.release, "relative.env"),
                                  (self.release, self.root / "missing.env"),
                                  (self.release, self.release / "operator.env")):
            with self.subTest(release=release, env_file=env_file):
                if Path(env_file).name == "operator.env":
                    Path(env_file).write_text("secret")
                self.assert_failed_safely(self.run_deploy(release=release, env_file=env_file))
                self.assertEqual(self.commands(), [])

    def test_missing_runtime_file_never_calls_docker(self):
        (self.release / "deployment/zitadel/bootstrap.py").unlink()
        self.assert_failed_safely(self.run_deploy())
        self.assertEqual(self.commands(), [])

    def test_first_install_bootstraps_before_api_and_records_verified_digests(self):
        self.record.unlink()
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.commands()
        stages = [call for call in calls if call[0] == "docker" and "up" in call]
        self.assertEqual([call[-1] for call in stages], ["redis", "zitadel", "zitadel-bootstrap", "api", "panel-app", "nginx"])
        self.assertEqual(stages[0][-2:], ["postgres", "redis"])
        self.assertIn("--exit-code-from", stages[2])
        self.assertIn("--force-recreate", stages[2])
        for call in (*stages[:2], *stages[3:]):
            self.assertIn("--wait", call)
            self.assertIn("--wait-timeout", call)
        for call in calls:
            if call[0] == "docker":
                self.assertIn("--project-name", call)
                self.assertEqual(call[call.index("--project-name") + 1], "kairos")
        self.assertEqual(json.loads(self.record.read_text()), self.manifest)
        override = json.loads((self.release / "compose.images.json").read_text())
        self.assertEqual(override, {"services": {service: {"image": image} for service, image in self.manifest["images"].items()}})
        self.assertEqual([call[-1] for call in calls if call[0] == "curl"], [
            "https://customer.example.com/", "https://panel.example.com/", "https://api.example.com/api/auth/v1/csrf"])
        self.assertEqual(self.env_file.read_text(), "POSTGRES_PASSWORD=do-not-print-this-secret\n")

    def test_failed_stage_stops_rollout_and_preserves_previous_record(self):
        for stage in ("pull", "redis", "zitadel", "zitadel-bootstrap", "api", "nginx"):
            with self.subTest(stage=stage):
                self.log.unlink(missing_ok=True)
                result = self.run_deploy(FAIL_STAGE=stage)
                self.assert_failed_safely(result)
                self.assertIn(stage, result.stderr)
                updates = [call[-1] for call in self.commands() if "up" in call]
                if stage in ("pull", "redis", "zitadel", "zitadel-bootstrap", "api"):
                    self.assertNotIn("panel-app", updates)
                    self.assertNotIn("nginx", updates)
                self.assertFalse(any(call[0] == "curl" for call in self.commands()))

    def test_failed_external_probe_never_records_success(self):
        for extra in ({"FAIL_STAGE": "external"}, {"PROBE_STATUS": "302"},
                      {"PROBE_STATUS": "503"}, {"PROBE_BODY": "gateway-only"}, {"PROBE_BODY": '{"token":""}'}):
            with self.subTest(extra=extra):
                self.log.unlink(missing_ok=True)
                self.assert_failed_safely(self.run_deploy(**extra))
                self.assertLessEqual(sum(call[0] == "curl" for call in self.commands()), 15)

    def test_host_lock_rejects_concurrent_deployment_before_docker(self):
        with (self.state / "deploy.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.run_deploy()
        self.assert_failed_safely(result)
        self.assertIn("host lock", result.stderr)
        self.assertEqual(self.commands(), [])

    def test_missing_external_secret_never_mutates_docker(self):
        (self.secrets / "zitadel-masterkey").unlink()
        self.assert_failed_safely(self.run_deploy())
        self.assertTrue(all("config" in call for call in self.commands()))

    @unittest.skipUnless(DOCKER, "Docker Compose CLI required for hosted configuration check")
    def test_absolute_external_configuration_deploys_with_real_compose_parser(self):
        self.env_file.write_text((ROOT / ".env.example").read_text() + "\n" + "\n".join([
            "WEBHOOK_DESTINATION_POLICY=PUBLIC_HTTPS",
            "KAIROS_APPLICATION_SECRETS_DIRECTORY=" + str(self.secrets),
            "KAIROS_TLS_DIRECTORY=" + str(self.secrets),
        ]) + "\n")
        result = self.run_deploy(REAL_COMPOSE=DOCKER)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(self.record.read_text()), self.manifest)

    @unittest.skipUnless(DOCKER, "Docker Compose CLI required for path-resolution regression")
    def test_relative_secret_paths_rejected_before_docker_mutation(self):
        # Compose normally rewrites relative bind paths to absolute paths. Test
        # the consumer against the actual parser, without contacting a daemon.
        self.env_file.write_text((ROOT / ".env.example").read_text() + "\n" + "\n".join([
            "WEBHOOK_DESTINATION_POLICY=PUBLIC_HTTPS",
            "KAIROS_APPLICATION_SECRETS_DIRECTORY=../secrets",
            "KAIROS_TLS_DIRECTORY=../secrets",
        ]) + "\n")
        result = self.run_deploy(REAL_COMPOSE=DOCKER)
        self.assert_failed_safely(result)
        self.assertTrue(all("config" in call for call in self.commands()))

    def test_unset_compose_environment_never_mutates_docker(self):
        self.config["services"]["postgres"]["environment"]["POSTGRES_PASSWORD"] = None
        self.assert_failed_safely(self.run_deploy())
        self.assertTrue(all("config" in call for call in self.commands()))

    def test_tls_symlink_into_release_never_mutates_docker(self):
        key = self.secrets / "tls.key"
        key.unlink()
        inside = self.release / "private.key"
        inside.write_text("fixture")
        key.symlink_to(inside)
        self.assert_failed_safely(self.run_deploy())
        self.assertTrue(all("config" in call for call in self.commands()))

    def test_shell_rejects_wrong_argument_count(self):
        result = subprocess.run(["bash", str(ROOT / "deployment/deploy.sh")], capture_output=True, text=True)
        self.assertEqual(result.returncode, 64)

    def test_unsafe_resolved_configuration_never_mutates_docker(self):
        for key, value in (("WEBHOOK_DESTINATION_POLICY", "LOCAL_DEVELOPMENT"),
                           ("NEXT_PUBLIC_API_BASE_URL", "http://api.example.com"),
                           ("NEXT_PUBLIC_API_BASE_URL", "https://wrong.example.com")):
            with self.subTest(key=key):
                original = self.config["services"]["api"]["environment"][key]
                self.config["services"]["api"]["environment"][key] = value
                result = self.run_deploy()
                self.assert_failed_safely(result)
                self.assertTrue(all("config" in call for call in self.commands()))
                self.config["services"]["api"]["environment"][key] = original


if __name__ == "__main__":
    unittest.main()

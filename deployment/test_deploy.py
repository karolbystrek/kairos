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
    "compose.yaml", "compose.production.yaml", "nginx/default.conf.template",
    "nginx/cloudflare-real-ip.conf",
    "deployment/postgres/bootstrap.sh",
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
        self.record.write_text(json.dumps(self.manifest))
        self.original_record = self.record.read_bytes()
        self.secrets = self.root / "secrets"
        self.secrets.mkdir()
        for name in ("webhook-encryption.bin", "email-encryption.bin", "vapid-private.pem", "vapid-public.pem",
                     "push-subscription-encryption.bin", "tls.crt", "tls.key"):
            (self.secrets / name).write_text("fixture-private-material")
        origins = {
            "NEXT_PUBLIC_CUSTOMER_APP_URL": "https://customer.example.com",
            "PANEL_APP_URL": "https://panel.example.com",
            "NEXT_PUBLIC_API_BASE_URL": "https://api.example.com",
        }
        self.config = {"services": {
            name: {"image": self.manifest["images"].get(name, "fixture:infra"), "environment": {}}
            for name in (*SERVICES, "nginx", "postgres", "redis")
        }}
        self.config["services"]["api"]["environment"] = {
            **origins, "WEBHOOK_DESTINATION_POLICY": "PUBLIC_HTTPS", "PUSH_DESTINATION_POLICY": "PUBLIC_HTTPS",
        }
        self.config["services"]["nginx"]["environment"] = {
            "KAIROS_CUSTOMER_APP_HOST": "customer.example.com", "KAIROS_PANEL_APP_HOST": "panel.example.com",
            "KAIROS_API_HOST": "api.example.com",
        }
        self.config["secrets"] = {
            name: {"file": str(self.secrets / name)}
            for name in ("webhook-encryption.bin", "email-encryption.bin", "vapid-private.pem", "vapid-public.pem",
                         "push-subscription-encryption.bin")
        }
        self.config["secrets"]["tls"] = {"file": str(self.secrets)}
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
    if os.environ.get('FAIL_STAGE') in ('pull', 'redis', 'api', 'nginx'):
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
if os.environ.get('FAIL_STAGE') == 'timeout':
    real_run = deploy.subprocess.run
    def run(args, **kwargs):
        if args[0] == 'docker' and 'up' in args and args[-1] == 'api':
            raise deploy.subprocess.TimeoutExpired(args, kwargs['timeout'], output='do-not-print-this-secret')
        return real_run(args, **kwargs)
    deploy.subprocess.run = run
sys.exit(deploy.main(sys.argv[1:]))
"""
        interpreter = self.bin / "python3"
        interpreter.write_text(runner)
        interpreter.chmod(0o755)
        return subprocess.run(
            [str(interpreter), str(ROOT / "deployment/deploy.py"),
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
        (self.release / "deployment/postgres/bootstrap.sh").unlink()
        self.assert_failed_safely(self.run_deploy())
        self.assertEqual(self.commands(), [])

    def test_first_install_bootstraps_before_api_and_records_verified_digests(self):
        self.record.unlink()
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.commands()
        stages = [call for call in calls if call[0] == "docker" and "up" in call]
        self.assertEqual([call[-1] for call in stages], ["redis", "api", "panel-app", "nginx"])
        self.assertEqual(stages[0][-2:], ["postgres", "redis"])
        for call in stages:
            self.assertIn("--wait", call)
            self.assertIn("--wait-timeout", call)
        for call in calls:
            if call[0] == "docker":
                self.assertIn("--project-name", call)
                self.assertEqual(call[call.index("--project-name") + 1], "kairos")
        self.assertEqual({key: json.loads(self.record.read_text())[key] for key in self.manifest}, self.manifest)
        override = json.loads((self.release / "compose.images.json").read_text())
        self.assertEqual(override, {"services": {service: {"image": image} for service, image in self.manifest["images"].items()}})
        self.assertEqual([call[-1] for call in calls if call[0] == "curl"], [
            "https://customer.example.com/", "https://panel.example.com/", "https://api.example.com/api/auth/v1/csrf"])
        self.assertEqual(self.env_file.read_text(), "POSTGRES_PASSWORD=do-not-print-this-secret\n")

    def test_failed_stage_stops_rollout_and_preserves_previous_record(self):
        for stage in ("pull", "redis", "api", "nginx"):
            with self.subTest(stage=stage):
                self.log.unlink(missing_ok=True)
                result = self.run_deploy(FAIL_STAGE=stage)
                self.assert_failed_safely(result)
                self.assertIn(stage, result.stderr)
                updates = [call[-1] for call in self.commands() if "up" in call]
                if stage in ("pull", "redis", "api"):
                    self.assertNotIn("panel-app", updates)
                    self.assertNotIn("nginx", updates)
                self.assertFalse(any(call[0] == "curl" for call in self.commands()))

    def test_failed_release_uses_previous_runtime_and_protected_environment(self):
        self.record.unlink()
        self.assertEqual(self.run_deploy().returncode, 0)
        previous = json.loads(self.record.read_text())
        previous_runtime = self.root / "previous runtime"
        shutil.copytree(self.release, previous_runtime)
        previous["release_directory"] = str(previous_runtime)
        self.record.write_text(json.dumps(previous))
        self.env_file.write_text("POSTGRES_PASSWORD=replacement-secret\n")
        self.log.unlink()
        changed = copy.deepcopy(self.manifest)
        changed["revision"] = "b" * 40
        changed["images"]["api"] = "ghcr.io/karolbystrek/kairos/api@sha256:" + "9" * 64
        result = self.run_deploy(changed)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Rollback verified", result.stderr)
        calls = [call for call in self.commands() if call[0] == "docker" and "up" in call]
        self.assertTrue(calls)
        for call in calls:
            self.assertEqual(Path(call[call.index("--project-directory") + 1]).resolve(), previous_runtime.resolve())
            snapshot = Path(call[call.index("--env-file") + 1])
            self.assertEqual(snapshot.read_text(), "POSTGRES_PASSWORD=do-not-print-this-secret\n")
            self.assertEqual(snapshot.stat().st_mode & 0o777, 0o600)
        self.assertEqual(json.loads(self.record.read_text()), previous)
        self.assertNotIn("replacement-secret", result.stdout + result.stderr)

    def test_success_retains_previous_recovery_identity_and_retry_does_not_replace_it(self):
        self.record.unlink()
        self.assertEqual(self.run_deploy().returncode, 0)
        first = json.loads(self.record.read_text())
        old_runtime = self.root / "first runtime"
        shutil.copytree(self.release, old_runtime)
        first["release_directory"] = str(old_runtime.resolve())
        self.record.write_text(json.dumps(first))
        second_manifest = {**self.manifest, "revision": "b" * 40}
        self.assertEqual(self.run_deploy(second_manifest).returncode, 0)
        second = json.loads(self.record.read_text())
        self.assertEqual(second["previous"], first)
        self.assertEqual(self.run_deploy(second_manifest).returncode, 0)
        self.assertEqual(json.loads(self.record.read_text())["previous"], first)

    def test_invalid_recovery_snapshot_fails_before_docker_without_exposing_contents(self):
        self.record.unlink()
        self.assertEqual(self.run_deploy().returncode, 0)
        original = json.loads(self.record.read_text())
        for env_path in (str(self.env_file), "relative.env"):
            with self.subTest(env_path=env_path):
                self.log.unlink(missing_ok=True)
                self.record.write_text(json.dumps({**original, "env_file": env_path}))
                result = self.run_deploy()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.commands(), [])
                self.assertNotIn("do-not-print-this-secret", result.stdout + result.stderr)
        self.record.write_text(json.dumps(original))
        Path(original["env_file"]).chmod(0o644)
        self.log.unlink(missing_ok=True)
        self.assertNotEqual(self.run_deploy().returncode, 0)
        self.assertEqual(self.commands(), [])

    def test_failed_external_probe_never_records_success(self):
        for extra in ({"FAIL_STAGE": "external"}, {"PROBE_STATUS": "302"},
                      {"PROBE_STATUS": "503"}, {"PROBE_BODY": "gateway-only"}, {"PROBE_BODY": '{"token":""}'}):
            with self.subTest(extra=extra):
                self.log.unlink(missing_ok=True)
                self.assert_failed_safely(self.run_deploy(**extra))
                self.assertLessEqual(sum(call[0] == "curl" for call in self.commands()), 15)

    def test_safe_failure_details_explain_validation_commands_and_probes(self):
        invalid = {**self.manifest, "revision": "invalid"}
        result = self.run_deploy(invalid)
        self.assert_failed_safely(result)
        self.assertIn("invalid release revision", result.stderr)
        for extra, expected in (
            ({"FAIL_STAGE": "api"}, "exit status 1"),
            ({"FAIL_STAGE": "timeout"}, "timed out after 360 seconds"),
            ({"PROBE_STATUS": "503"}, "HTTP 503"),
            ({"FAIL_STAGE": "external"}, "exit status 7"),
            ({"PROBE_BODY": "do-not-print-this-secret"}, "invalid JSON"),
        ):
            with self.subTest(extra=extra):
                self.log.unlink(missing_ok=True)
                result = self.run_deploy(**extra)
                self.assert_failed_safely(result)
                self.assertIn(expected, result.stderr)

    def test_host_lock_rejects_concurrent_deployment_before_docker(self):
        with (self.state / "deploy.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.run_deploy()
        self.assert_failed_safely(result)
        self.assertIn("host lock", result.stderr)
        self.assertEqual(self.commands(), [])

    def test_missing_external_secret_never_mutates_docker(self):
        (self.secrets / "email-encryption.bin").unlink()
        self.assert_failed_safely(self.run_deploy())
        self.assertTrue(all("config" in call for call in self.commands()))

    @unittest.skipUnless(DOCKER, "Docker Compose CLI required for hosted configuration check")
    def test_absolute_external_configuration_deploys_with_real_compose_parser(self):
        self.env_file.write_text((ROOT / ".env.example").read_text() + "\n" + "\n".join([
            "WEBHOOK_DESTINATION_POLICY=PUBLIC_HTTPS",
            "RESEND_API_KEY=fixture-resend-key",
            "KAIROS_APPLICATION_SECRETS_DIRECTORY=" + str(self.secrets),
            "KAIROS_TLS_DIRECTORY=" + str(self.secrets),
            # Local .env.example has no gateway; supply the production-only NGINX values and origins.
            "NEXT_PUBLIC_CUSTOMER_APP_URL=https://customer.kairos.localhost",
            "PANEL_APP_URL=https://panel.kairos.localhost",
            "NEXT_PUBLIC_API_BASE_URL=https://api.kairos.localhost",
            "KAIROS_CUSTOMER_APP_HOST=customer.kairos.localhost",
            "KAIROS_PANEL_APP_HOST=panel.kairos.localhost",
            "KAIROS_API_HOST=api.kairos.localhost",
            "NGINX_ENVSUBST_FILTER=^KAIROS_",
        ]) + "\n")
        result = self.run_deploy(REAL_COMPOSE=DOCKER)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual({key: json.loads(self.record.read_text())[key] for key in self.manifest}, self.manifest)
        config_call = next(call for call in self.commands() if "config" in call)
        resolved = subprocess.run(
            [DOCKER, *config_call[1:]], capture_output=True, text=True, check=True,
            env={**os.environ, "KAIROS_IMAGE_REGISTRY": "ghcr.io/karolbystrek/kairos",
                 "KAIROS_RELEASE_VERSION": REVISION, "KAIROS_API_USER": "1000:1000"},
        )
        services = json.loads(resolved.stdout)["services"]
        self.assertEqual("1000:1000", services["api"]["user"])
        postgres = services["postgres"]
        self.assertNotIn("postgres-bootstrap", services)
        self.assertNotIn("POSTGRES_USER", services["api"]["environment"])
        self.assertNotIn("POSTGRES_PASSWORD", services["api"]["environment"])
        for name in ("KAIROS_DB_OWNER_USER", "KAIROS_DB_OWNER_PASSWORD",
                     "KAIROS_DB_RUNTIME_USER", "KAIROS_DB_RUNTIME_PASSWORD"):
            self.assertEqual(postgres["environment"][name], services["api"]["environment"][name])
        role_init = next(volume for volume in postgres["volumes"]
                         if volume["target"] == "/docker-entrypoint-initdb.d/10-kairos-roles.sh")
        self.assertEqual(role_init["source"], "./deployment/postgres/bootstrap.sh")
        self.assertTrue(role_init["read_only"])

    @unittest.skipUnless(DOCKER, "Docker Compose CLI required for path-resolution regression")
    def test_relative_secret_paths_rejected_before_docker_mutation(self):
        # Compose normally rewrites relative bind paths to absolute paths. Test
        # the consumer against the actual parser, without contacting a daemon.
        self.env_file.write_text((ROOT / ".env.example").read_text() + "\n" + "\n".join([
            "WEBHOOK_DESTINATION_POLICY=PUBLIC_HTTPS",
            "RESEND_API_KEY=fixture-resend-key",
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

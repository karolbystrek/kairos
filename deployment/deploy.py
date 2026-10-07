"""Deploy a complete digest-pinned release; never reset data or generate secrets."""
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlsplit

STATE_DIRECTORY = Path("/srv/kairos")
SERVICES = ("customer-app", "panel-app", "api")
RUNTIME_FILES = (
    "compose.yaml", "compose.deployment.yaml", "nginx/default.conf.template",
    "nginx/cloudflare-real-ip.conf", "deployment/zitadel/bootstrap.py",
)


class ValidationError(ValueError):
    """An explanation written by this command, never external output."""


def failure_reason(error):
    if isinstance(error, ValidationError):
        return str(error)
    if isinstance(error, subprocess.CalledProcessError):
        return f"command failed with exit status {error.returncode}"
    if isinstance(error, subprocess.TimeoutExpired):
        return f"command timed out after {error.timeout} seconds"
    if isinstance(error, json.JSONDecodeError):
        return "invalid JSON"
    if isinstance(error, OSError) and error.errno is not None:
        return f"OS error {error.errno}: {os.strerror(error.errno)}"
    return "invalid or incomplete configuration"


def inputs(arguments):
    if len(arguments) != 2 or any(not Path(arg).is_absolute() for arg in arguments):
        raise ValidationError("two absolute paths are required")
    release, env_file = (Path(arg).resolve(strict=True) for arg in arguments)
    if not release.is_dir() or not env_file.is_file() or env_file.is_relative_to(release):
        raise ValidationError("environment file must be external to the release directory")
    for name in ("release.json", *RUNTIME_FILES):
        path = release / name
        if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to(release):
            raise ValidationError("release runtime files must be present and regular")
    manifest = json.loads((release / "release.json").read_text())
    if not isinstance(manifest, dict) or set(manifest) != {"revision", "images"}:
        raise ValidationError("invalid release manifest")
    revision, images = manifest["revision"], manifest["images"]
    if not isinstance(revision, str) or not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValidationError("invalid release revision")
    if not isinstance(images, dict) or set(images) != set(SERVICES):
        raise ValidationError("release requires exactly three application images")
    for service, image in images.items():
        pattern = rf"ghcr\.io/karolbystrek/kairos/{service}@sha256:[0-9a-f]{{64}}"
        if not isinstance(image, str) or not re.fullmatch(pattern, image):
            raise ValidationError("invalid repository-owned image digest")
    return release, env_file, manifest


def atomic_json(path, value):
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", dir=path.parent, delete=False) as file:
            temporary = Path(file.name)
            json.dump(value, file, indent=2)
            file.write("\n")
            file.flush()
            os.fsync(file.fileno())
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def command(args, env, *, capture=False, timeout=900):
    # Arguments and raw output can contain secrets; report error metadata only.
    return subprocess.run(args, env=env, check=True, timeout=timeout,
                          stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, text=True).stdout


def configuration(config, release, images):
    services = config["services"]
    if any(value is None for service in services.values() for value in service.get("environment", {}).values()):
        raise ValidationError("Compose environment values must be supplied")
    for name, image in images.items():
        if services[name]["image"] != image:
            raise ValidationError("Compose did not resolve the pinned application image")
    api = services["api"]["environment"]
    if any(api.get(name) != "PUBLIC_HTTPS" for name in ("WEBHOOK_DESTINATION_POLICY", "PUSH_DESTINATION_POLICY")):
        raise ValidationError("hosted delivery policies must require public HTTPS")
    gateway = services["nginx"]["environment"]
    origins = []
    for origin_key, host_key in (
        ("NEXT_PUBLIC_CUSTOMER_APP_URL", "KAIROS_CUSTOMER_APP_HOST"),
        ("PANEL_APP_URL", "KAIROS_PANEL_APP_HOST"),
        ("NEXT_PUBLIC_API_BASE_URL", "KAIROS_API_HOST"),
    ):
        origin = api[origin_key]
        url = urlsplit(origin)
        if (url.scheme != "https" or not url.hostname or url.netloc != gateway[host_key]
                or url.path not in ("", "/") or url.query or url.fragment or url.username):
            raise ValidationError("public HTTPS origins must match exact gateway hostnames")
        origins.append(origin.rstrip("/"))
    if len(set(origins)) != 3:
        raise ValidationError("three distinct public origins are required")
    # Bind mounts fail safely: no missing path may become an empty directory,
    # and persistent key/certificate material must live outside the checkout.
    for name in ("api", "zitadel", "nginx"):
        for volume in services[name].get("volumes", []):
            if volume["type"] != "bind":
                continue
            if name == "nginx" and volume.get("target") != "/run/secrets/tls":
                continue
            source = Path(volume["source"])
            if not source.is_absolute() or not source.exists():
                raise ValidationError("bind mount source must exist at an absolute path")
            if source.resolve().is_relative_to(release) or not volume.get("read_only"):
                raise ValidationError("persistent secrets must be external and read-only")
            if name == "nginx":
                if not all((source / file).is_file() and not (source / file).resolve().is_relative_to(release)
                           for file in ("tls.crt", "tls.key")):
                    raise ValidationError("external TLS certificate and key are required")
            elif not source.is_file():
                raise ValidationError("application key bind mounts must be regular files")
    return origins


def probe(url, env, csrf=False):
    with tempfile.TemporaryDirectory(prefix="kairos-probe-") as directory:
        body = Path(directory) / "response"
        for attempt in range(5):
            try:
                status = command([
                    "curl", "--disable", "--silent", "--show-error", "--noproxy", "*",
                    "--connect-timeout", "5", "--max-time", "15", "--max-filesize", "1048576",
                    "--output", str(body), "--write-out", "%{http_code}", url,
                ], env, capture=True, timeout=20)
                reason = f"HTTP {status}" if re.fullmatch(r"[0-9]{3}", status) else "invalid HTTP status"
                if status == "200":
                    if not csrf:
                        return
                    data = json.loads(body.read_text())
                    if isinstance(data, dict) and isinstance(data.get("token"), str) and data["token"].strip():
                        return
                    reason = "missing or invalid CSRF token"
            except (subprocess.SubprocessError, OSError, ValueError) as error:
                reason = failure_reason(error)
            if attempt < 4:
                time.sleep(2)
    raise ValidationError(f"public probe failed after 5 attempts: {reason}")


def main(arguments=None):
    stage = "inputs"
    try:
        release, env_file, manifest = inputs(sys.argv[1:] if arguments is None else arguments)
        env = {**os.environ, "COMPOSE_PROJECT_NAME": "kairos",
               "KAIROS_IMAGE_REGISTRY": "ghcr.io/karolbystrek/kairos",
               "KAIROS_RELEASE_VERSION": manifest["revision"]}
        stage = "host lock"
        STATE_DIRECTORY.mkdir(parents=True, exist_ok=True)
        with (STATE_DIRECTORY / "deploy.lock").open("a") as lock:
            # Host-wide flock, independent of the disposable release directory.
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            stage = "image override"
            override = release / "compose.images.json"
            atomic_json(override, {"services": {
                service: {"image": image} for service, image in manifest["images"].items()
            }})
            compose = ["docker", "compose", "--project-name", "kairos", "--project-directory", str(release),
                       "--env-file", str(env_file), "-f", str(release / "compose.yaml"),
                       "-f", str(release / "compose.deployment.yaml"), "-f", str(override)]
            stage = "Compose validation"
            resolved = json.loads(command([*compose, "config", "--format", "json", "--no-path-resolution"], env, capture=True, timeout=60))
            origins = configuration(resolved, release, manifest["images"])
            stage = "pull"
            command([*compose, "pull", "--policy", "always"], env)
            up = [*compose, "up", "--no-deps", "--no-build", "--pull", "never"]
            healthy = [*up, "--wait", "--wait-timeout", "300"]
            for stage, args in (
                ("postgres/redis", [*healthy, "postgres", "redis"]),
                ("zitadel", [*healthy, "zitadel"]),
                ("zitadel-bootstrap", [*up, "--force-recreate", "--exit-code-from", "zitadel-bootstrap", "zitadel-bootstrap"]),
                ("api", [*healthy, "--force-recreate", "api"]),
                ("frontends", [*healthy, "--force-recreate", "customer-app", "panel-app"]),
                ("nginx", [*healthy, "--force-recreate", "nginx"]),
            ):
                print(f"Deployment stage: {stage}", flush=True)
                command(args, env, timeout=360)
            stage = "customer HTTPS probe"
            probe(origins[0] + "/", env)
            stage = "panel HTTPS probe"
            probe(origins[1] + "/", env)
            stage = "API CSRF HTTPS probe"
            probe(origins[2] + "/api/auth/v1/csrf", env, csrf=True)
            stage = "success record"
            atomic_json(STATE_DIRECTORY / "deployed-release.json", manifest)
            print(f"Deployment verified: {manifest['revision']}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(f"Deployment failed at {stage}: {failure_reason(error)}.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

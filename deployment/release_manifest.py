"""Assemble a release from Docker inspections of all three published images."""
import json
from pathlib import Path
import re
import sys
import tarfile

SERVICES = ("customer-app", "panel-app", "api")
RUNTIME_FILES = (
    "compose.yaml",
    "compose.deployment.yaml",
    "nginx/default.conf.template",
    "nginx/cloudflare-real-ip.conf",
    "deployment/zitadel/bootstrap.py",
    "deployment/deploy.sh",
    "deployment/deploy.py",
)


def assemble(revision, inspections, output):
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("release revision must be a full lowercase Git SHA")
    images = {}
    for service in SERVICES:
        records = json.loads((inspections / f"{service}.json").read_text())
        if len(records) != 1:
            raise ValueError(f"{service}: expected one inspected image")
        image = records[0]
        labels = image.get("Config", {}).get("Labels") or {}
        if (image.get("Os"), image.get("Architecture")) != ("linux", "amd64"):
            raise ValueError(f"{service}: expected linux/amd64")
        if labels.get("org.opencontainers.image.revision") != revision:
            raise ValueError(f"{service}: source revision mismatch")
        pattern = rf"ghcr\.io/karolbystrek/kairos/{service}@sha256:[0-9a-f]{{64}}"
        digests = [ref for ref in image.get("RepoDigests", []) if re.fullmatch(pattern, ref)]
        if len(digests) != 1:
            raise ValueError(f"{service}: expected one repository-owned registry digest")
        images[service] = digests[0]

    root = Path(__file__).resolve().parent.parent
    # Validate the entire allowlist before writing any release output.
    for name in RUNTIME_FILES:
        if not (root / name).is_file() or (root / name).is_symlink():
            raise ValueError(f"missing regular runtime file: {name}")
    output.mkdir(parents=True, exist_ok=False)
    manifest = output / "release.json"
    manifest.write_text(json.dumps({"revision": revision, "images": images}, indent=2) + "\n")
    with tarfile.open(output / "release.tar.gz", "w:gz") as archive:
        archive.add(manifest, arcname="release.json")
        for name in RUNTIME_FILES:
            archive.add(root / name, arcname=name, recursive=False)


if __name__ == "__main__":
    try:
        assemble(sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3]))
    except (ValueError, OSError, KeyError, TypeError, IndexError) as error:
        sys.exit(f"release assembly failed: {error}")

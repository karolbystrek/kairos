#!/bin/sh
# Executes the real configuration tasks locally; never touches Docker or SSH.
set -eu
repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
test_directory="$(mktemp -d "${TMPDIR:-/tmp}/kairos-bootstrap-test.XXXXXX")"
trap 'rm -rf -- "${test_directory}"' EXIT HUP INT TERM
command -v ansible-playbook >/dev/null
printf '[kairos]\nlocalhost ansible_connection=local\n' > "$test_directory/inventory.ini"
python3 - "$test_directory" <<'PY'
import getpass, json, os, sys
from pathlib import Path
root = Path(sys.argv[1])
(root / 'vars.json').write_text(json.dumps({
    'ansible_become': False,
    'ansible_python_interpreter': sys.executable,
    'kairos_deploy_user': getpass.getuser(),
    'kairos_directory': str(root / 'host'),
}))
PY
run_configuration() {
    ansible-playbook -i "$test_directory/inventory.ini" \
        "$repository_directory/deployment/bootstrap.yml" \
        --tags configuration -e "@$test_directory/vars.json" \
        > "$test_directory/output" 2>&1
}
if ! run_configuration; then
    cat "$test_directory/output" >&2
    echo 'Bootstrap failed to prepare isolated configuration.' >&2
    exit 1
fi
# Fresh bootstrap leaves only a template, never deployable local defaults.
test ! -e "$test_directory/host/production.env"
test -f "$test_directory/host/production.env.example"
cp -R "$test_directory/host/secrets" "$test_directory/expected-secrets"
printf '%s\n' 'operator-owned-production-configuration' > "$test_directory/host/production.env"
cp "$test_directory/host/production.env" "$test_directory/expected.env"
run_configuration || { cat "$test_directory/output" >&2; exit 1; }
cmp "$test_directory/expected.env" "$test_directory/host/production.env"
for key in zitadel-masterkey webhook-encryption.bin vapid-private.pem vapid-public.pem push-subscription-encryption.bin; do
    cmp "$test_directory/expected-secrets/$key" "$test_directory/host/secrets/$key"
done
python3 - "$test_directory/host" <<'PY'
from pathlib import Path
import stat, sys
root = Path(sys.argv[1])
for path in [root, root/'secrets', root/'releases', root/'tls']:
    assert stat.S_IMODE(path.stat().st_mode) == 0o700, path
for path in (root/'secrets').iterdir():
    assert stat.S_IMODE(path.stat().st_mode) == 0o400, path
assert stat.S_IMODE((root/'production.env.example').stat().st_mode) == 0o600
assert stat.S_IMODE((root/'production.env').stat().st_mode) == 0o600
assert not list(root.glob('.bootstrap.*')), 'Temporary setup files leaked'
PY
# Invalid material must fail rather than silently regenerating credentials.
chmod 0600 "$test_directory/host/secrets/webhook-encryption.bin"
printf '%s' broken > "$test_directory/host/secrets/webhook-encryption.bin"
if run_configuration; then
    echo 'Bootstrap accepted an invalid key set.' >&2
    exit 1
fi
cmp "$test_directory/expected.env" "$test_directory/host/production.env"
test "$(cat "$test_directory/host/secrets/webhook-encryption.bin")" = broken
rm "$test_directory/host/secrets/vapid-public.pem"
if run_configuration; then
    echo 'Bootstrap accepted a partial key set.' >&2
    exit 1
fi
test ! -e "$test_directory/host/secrets/vapid-public.pem"
printf '%s\n' 'Bootstrap generation, permissions, rerun preservation and invalid/partial-key refusal passed.'

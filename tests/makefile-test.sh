#!/bin/sh
# Runs the real Makefile in a scratch copy with Docker replaced by a recorder.
set -eu
repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
test_directory="$(mktemp -d "${TMPDIR:-/tmp}/kairos-makefile-test.XXXXXX")"
trap 'rm -rf -- "${test_directory}"' EXIT HUP INT TERM
work="${test_directory}/work"
mkdir -p "${work}" "${test_directory}/bin"
cp "${repository_directory}/Makefile" "${repository_directory}/.env.example" "${work}/"
export KAIROS_DOCKER_CALLS="${test_directory}/calls"
printf '#!/bin/sh\nprintf "%%s\\n" "$*" >> "$KAIROS_DOCKER_CALLS"\n' > "${test_directory}/bin/docker"
chmod +x "${test_directory}/bin/docker"
run_make() { PATH="${test_directory}/bin:${PATH}" make -C "${work}" "$@" > "${test_directory}/output" 2>&1; }
keys="email-encryption.bin webhook-encryption.bin push-subscription-encryption.bin vapid-private.pem vapid-public.pem"

run_make dev || { cat "${test_directory}/output" >&2; exit 1; }
cmp "${work}/.env.example" "${work}/.env"
python3 - "${work}" ${keys} <<'PY'
import stat, subprocess, sys
from pathlib import Path
work = Path(sys.argv[1])
mode = lambda p: stat.S_IMODE(p.stat().st_mode)
assert mode(work / '.env') == 0o600, '.env is not 0600'
assert mode(work / 'secrets') == 0o700, 'secrets/ is not 0700'
for name in sys.argv[2:]:
    assert mode(work / 'secrets' / name) == 0o400, f'{name} is not 0400'
    if name.endswith('.bin'):
        assert (work / 'secrets' / name).stat().st_size == 32, f'{name} is not 32 bytes'
derived = subprocess.run(['openssl', 'pkey', '-in', work / 'secrets/vapid-private.pem', '-pubout'], capture_output=True, check=True).stdout
assert derived == (work / 'secrets/vapid-public.pem').read_bytes(), 'VAPID public key does not match the private key'
PY
grep -qx 'compose up --build -d --wait' "${KAIROS_DOCKER_CALLS}"

printf '%s\n' 'CUSTOM=kept' >> "${work}/.env"
cp -R "${work}/secrets" "${test_directory}/expected-secrets"
run_make dev || { cat "${test_directory}/output" >&2; exit 1; }
grep -qx 'CUSTOM=kept' "${work}/.env" || { echo 'make dev replaced an existing .env' >&2; exit 1; }
diff -r "${test_directory}/expected-secrets" "${work}/secrets" || { echo 'make dev replaced existing keys' >&2; exit 1; }

run_make reset || { cat "${test_directory}/output" >&2; exit 1; }
grep -qx 'compose --env-file .env.example --env-file .env down -v --remove-orphans' "${KAIROS_DOCKER_CALLS}"
[ ! -e "${work}/.env" ]
grep -qx 'CUSTOM=kept' "${work}"/.env.old.* || { echo 'make reset did not keep the old .env' >&2; exit 1; }
for key in ${keys}; do
    [ ! -e "${work}/secrets/${key}" ] || { echo "make reset kept secrets/${key}" >&2; exit 1; }
done

: > "${KAIROS_DOCKER_CALLS}"
echo 'COMPOSE_FILE=compose.yaml:compose.production.yaml' > "${work}/.env"
echo fixture > "${work}/secrets/webhook-encryption.bin"
if run_make reset; then
    echo 'make reset ran against production' >&2; exit 1
fi
grep -q 'Refusing to reset' "${test_directory}/output"
[ ! -s "${KAIROS_DOCKER_CALLS}" ] && [ -f "${work}/.env" ] && [ -f "${work}/secrets/webhook-encryption.bin" ]
printf '%s\n' 'Makefile key generation, rerun preservation, reset and production refusal passed.'

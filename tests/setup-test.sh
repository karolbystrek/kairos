#!/bin/sh
set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
test_directory="$(mktemp -d "${TMPDIR:-/tmp}/kairos-setup-test.XXXXXX")"
trap 'rm -rf -- "${test_directory}"' EXIT HUP INT TERM
cp "${repository_directory}/setup.sh" "${repository_directory}/.env.example" "${test_directory}/"

sh "${test_directory}/setup.sh" --non-interactive --secrets-directory secrets > "${test_directory}/output"
for key_name in webhook-encryption.bin email-encryption.bin vapid-private.pem vapid-public.pem push-subscription-encryption.bin; do
    [ -f "${test_directory}/secrets/${key_name}" ]
done
[ "$(wc -c < "${test_directory}/secrets/email-encryption.bin" | tr -d ' ')" -eq 32 ]
cp "${test_directory}/secrets/email-encryption.bin" "${test_directory}/expected-email"
printf '%s' 'externally-supplied-credential' > "${test_directory}/secrets/external-secret.txt"
cp "${test_directory}/secrets/external-secret.txt" "${test_directory}/expected-credential"
cp "${test_directory}/secrets/webhook-encryption.bin" "${test_directory}/expected-webhook"
mkdir "${test_directory}/secrets/zitadel"
# Open directory/file descriptors expose replacements even if paths still exist.
same_inode() {
    python3 - "$1" "$2" <<'CHECK'
import os, sys
path, descriptor = os.stat(sys.argv[1]), os.fstat(int(sys.argv[2]))
assert (path.st_dev, path.st_ino) == (descriptor.st_dev, descriptor.st_ino), sys.argv[1]
CHECK
}
exec 3< "${test_directory}/secrets"
exec 4< "${test_directory}/secrets/zitadel"
exec 5< "${test_directory}/secrets/webhook-encryption.bin"
sh "${test_directory}/setup.sh" --non-interactive --keep-env --keep-keys --secrets-directory secrets > "${test_directory}/output"
same_inode "${test_directory}/secrets" 3 || { echo "Setup replaced the secrets directory." >&2; exit 1; }
same_inode "${test_directory}/secrets/zitadel" 4 || { echo "Setup replaced the ZITADEL credentials directory." >&2; exit 1; }
same_inode "${test_directory}/secrets/webhook-encryption.bin" 5 || { echo "Setup replaced a reused key." >&2; exit 1; }
cmp "${test_directory}/expected-email" "${test_directory}/secrets/email-encryption.bin"
cmp "${test_directory}/expected-webhook" "${test_directory}/secrets/webhook-encryption.bin"
sh "${test_directory}/setup.sh" --non-interactive --keep-env --replace-keys --secrets-directory secrets > "${test_directory}/output"
same_inode "${test_directory}/secrets" 3
same_inode "${test_directory}/secrets/zitadel" 4
cmp "${test_directory}/expected-credential" "${test_directory}/secrets/external-secret.txt"
if cmp -s "${test_directory}/expected-webhook" "${test_directory}/secrets/webhook-encryption.bin"; then
    echo "Application key replacement did not replace its keys." >&2
    exit 1
fi
mkdir "${test_directory}/bin"
cp -R "${test_directory}/secrets" "${test_directory}/expected-secrets"
cat > "${test_directory}/bin/mv" <<'MOCK'
#!/bin/sh
case "$*" in
    */.kairos-setup.*/secrets/vapid-private.pem*) exit 1 ;;
esac
exec /bin/mv "$@"
MOCK
chmod +x "${test_directory}/bin/mv"
if PATH="${test_directory}/bin:${PATH}" sh "${test_directory}/setup.sh" --non-interactive --keep-env --replace-keys --secrets-directory secrets > "${test_directory}/output" 2>&1; then
    echo "Setup accepted a failed file installation." >&2
    exit 1
fi
diff -r "${test_directory}/expected-secrets" "${test_directory}/secrets"
same_inode "${test_directory}/secrets" 3
same_inode "${test_directory}/secrets/zitadel" 4
# Presets need no other options: --dev creates .env and secrets/, --prod never writes .env.
preset_directory="${test_directory}/preset"
mkdir "${preset_directory}"
cp "${repository_directory}/setup.sh" "${repository_directory}/.env.example" "${preset_directory}/"
sh "${preset_directory}/setup.sh" --dev > "${test_directory}/output"
cmp "${preset_directory}/.env.example" "${preset_directory}/.env"
cp "${preset_directory}/secrets/webhook-encryption.bin" "${test_directory}/expected-dev-webhook"
printf '%s\n' 'CUSTOM=kept' >> "${preset_directory}/.env"
sh "${preset_directory}/setup.sh" --dev > "${test_directory}/output"
grep -q '^CUSTOM=kept$' "${preset_directory}/.env"
cmp "${test_directory}/expected-dev-webhook" "${preset_directory}/secrets/webhook-encryption.bin"
rm "${preset_directory}/.env"
sh "${preset_directory}/setup.sh" --prod --secrets-directory "${test_directory}/production-secrets" > "${test_directory}/output"
[ ! -e "${preset_directory}/.env" ]
[ -f "${test_directory}/production-secrets/vapid-private.pem" ]
if sh "${preset_directory}/setup.sh" --dev --prod > "${test_directory}/output" 2>&1; then
    echo "Setup accepted both presets." >&2
    exit 1
fi
# Exercise mode selection against the real Compose parser, replacing only startup.
stack_directory="${test_directory}/stack"
mkdir -p "${stack_directory}/deployment" "${stack_directory}/bin"
cp "${repository_directory}/setup.sh" "${repository_directory}/.env.example" "${stack_directory}/"
cp "${repository_directory}/deployment/production.env.example" "${stack_directory}/deployment/"
printf 'services:\n  api:\n    image: alpine\n' > "${stack_directory}/compose.yaml"
for overlay in local production; do
    printf 'services: {}\n' > "${stack_directory}/compose.${overlay}.yaml"
done
cat > "${stack_directory}/compose.production.yaml" <<'YAML'
services:
  api:
    environment:
      RESEND_API_KEY: ${RESEND_API_KEY:?Set RESEND_API_KEY for production}
YAML
export KAIROS_TEST_DOCKER="$(command -v docker)"
export KAIROS_SETUP_CALLS="${stack_directory}/calls"
cat > "${stack_directory}/bin/docker" <<'MOCK'
#!/bin/sh
case " $* " in
    *" up "*)
        printf '%s\n' "$*" >> "$KAIROS_SETUP_CALLS"
        case " $* " in *" up --build -d --wait "*) exit 0;; *) exit 1;; esac
        ;;
    *) exec "$KAIROS_TEST_DOCKER" "$@" ;;
esac
MOCK
cat > "${stack_directory}/bin/npm" <<'MOCK'
#!/bin/sh
echo 'Setup must install dependencies inside Docker, not on the host.' >&2
exit 1
MOCK
chmod +x "${stack_directory}/bin/docker" "${stack_directory}/bin/npm"
# Existing host-run configuration is upgraded without replacing credentials/custom fields.
cat > "${stack_directory}/.env" <<'ENV'
POSTGRES_HOST=localhost
REDIS_HOST=localhost
KAIROS_SECRETS_DIRECTORY=../../secrets
POSTGRES_PASSWORD=preserved-password
CUSTOM=preserved
ENV
PATH="${stack_directory}/bin:${PATH}" sh "${stack_directory}/setup.sh" --dev > "${test_directory}/output"
grep -q '^COMPOSE_FILE=compose.yaml:compose.local.yaml$' "${stack_directory}/.env"
grep -q '^POSTGRES_HOST=postgres$' "${stack_directory}/.env"
grep -q '^REDIS_HOST=redis$' "${stack_directory}/.env"
grep -q '^KAIROS_SECRETS_DIRECTORY=/run/secrets$' "${stack_directory}/.env"
grep -q '^POSTGRES_PASSWORD=preserved-password$' "${stack_directory}/.env"
grep -q '^CUSTOM=preserved$' "${stack_directory}/.env"
cp "${stack_directory}/.env" "${test_directory}/expected-dev.env"
cp "${stack_directory}/secrets/webhook-encryption.bin" "${test_directory}/expected-stack-key"
PATH="${stack_directory}/bin:${PATH}" sh "${stack_directory}/setup.sh" --dev > "${test_directory}/output"
cmp "${test_directory}/expected-dev.env" "${stack_directory}/.env"
cmp "${test_directory}/expected-stack-key" "${stack_directory}/secrets/webhook-encryption.bin"
test "$(wc -l < "${stack_directory}/calls" | tr -d ' ')" -eq 2
# A fresh production selection prepares config, but cannot start with missing credentials/TLS.
mv "${stack_directory}/.env" "${stack_directory}/dev.env"
if PATH="${stack_directory}/bin:${PATH}" sh "${stack_directory}/setup.sh" --prod > "${test_directory}/output" 2>&1; then
    echo 'Production setup accepted missing production credentials and TLS.' >&2
    exit 1
fi
grep -q '^COMPOSE_FILE=compose.yaml:compose.production.yaml$' "${stack_directory}/.env"
grep -q 'RESEND_API_KEY' "${test_directory}/output"
test "$(wc -l < "${stack_directory}/calls" | tr -d ' ')" -eq 2
mkdir "${stack_directory}/tls"
touch "${stack_directory}/tls/tls.crt" "${stack_directory}/tls/tls.key"
cat >> "${stack_directory}/.env" <<ENV
POSTGRES_PASSWORD=production-postgres
KAIROS_DB_OWNER_PASSWORD=production-owner
KAIROS_DB_RUNTIME_PASSWORD=production-runtime
REDIS_PASSWORD=production-redis
RESEND_API_KEY=production-resend
VAPID_SUBJECT=mailto:operator@example.com
KAIROS_TLS_DIRECTORY=${stack_directory}/tls
CUSTOM=preserved-production
ENV
PATH="${stack_directory}/bin:${PATH}" sh "${stack_directory}/setup.sh" --prod > "${test_directory}/output"
grep -q '^CUSTOM=preserved-production$' "${stack_directory}/.env"
test "$(wc -l < "${stack_directory}/calls" | tr -d ' ')" -eq 3
cmp "${test_directory}/expected-stack-key" "${stack_directory}/secrets/webhook-encryption.bin"
# Selecting production from dev must preserve custom values and reject unsafe old defaults.
cp "${stack_directory}/dev.env" "${stack_directory}/.env"
printf '%s\n' 'RESEND_API_KEY=production-resend' >> "${stack_directory}/.env"
if PATH="${stack_directory}/bin:${PATH}" sh "${stack_directory}/setup.sh" --prod > "${test_directory}/output" 2>&1; then
    echo 'Production setup accepted local-only development configuration.' >&2
    exit 1
fi
grep -q '^CUSTOM=preserved$' "${stack_directory}/.env"
grep -q '^POSTGRES_PASSWORD=preserved-password$' "${stack_directory}/.env"
test "$(wc -l < "${stack_directory}/calls" | tr -d ' ')" -eq 3
printf '%s\n' 'Setup keys, rollback, Docker mode selection, config preservation and production validation passed.'

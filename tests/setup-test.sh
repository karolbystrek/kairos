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
printf '%s\n' 'Setup generation, reuse, directory preservation, rollback and presets passed.'

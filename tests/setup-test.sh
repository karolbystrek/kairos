#!/bin/sh
set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
test_directory="$(mktemp -d "${TMPDIR:-/tmp}/kairos-setup-test.XXXXXX")"
trap 'rm -rf -- "${test_directory}"' EXIT HUP INT TERM
cp "${repository_directory}/setup.sh" "${repository_directory}/.env.example" "${test_directory}/"

sh "${test_directory}/setup.sh" --non-interactive --secrets-directory secrets --no-tls > "${test_directory}/output"
for key_name in zitadel-masterkey webhook-encryption.bin vapid-private.pem vapid-public.pem push-subscription-encryption.bin; do
    [ -f "${test_directory}/secrets/${key_name}" ]
done
printf '%s' 'externally-supplied-credential' > "${test_directory}/secrets/external-secret.txt"
cp "${test_directory}/secrets/external-secret.txt" "${test_directory}/expected-credential"
cp "${test_directory}/secrets/zitadel-masterkey" "${test_directory}/expected-masterkey"
cp "${test_directory}/secrets/webhook-encryption.bin" "${test_directory}/expected-webhook"
sh "${test_directory}/setup.sh" --non-interactive --keep-env --keep-keys --secrets-directory secrets --no-tls > "${test_directory}/output"
cmp "${test_directory}/expected-webhook" "${test_directory}/secrets/webhook-encryption.bin"
sh "${test_directory}/setup.sh" --non-interactive --keep-env --replace-keys --secrets-directory secrets --no-tls > "${test_directory}/output"
cmp "${test_directory}/expected-masterkey" "${test_directory}/secrets/zitadel-masterkey"
cmp "${test_directory}/expected-credential" "${test_directory}/secrets/external-secret.txt"
if cmp -s "${test_directory}/expected-webhook" "${test_directory}/secrets/webhook-encryption.bin"; then
    echo "Application key replacement did not replace its keys." >&2
    exit 1
fi
printf '%s\n' 'Setup generation, reuse, and external-secret preservation passed.'

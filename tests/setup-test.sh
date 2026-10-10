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
mkdir "${test_directory}/secrets/tls"
# Open directory/file descriptors expose replacements even if paths still exist.
same_inode() {
    python3 - "$1" "$2" <<'CHECK'
import os, sys
path, descriptor = os.stat(sys.argv[1]), os.fstat(int(sys.argv[2]))
assert (path.st_dev, path.st_ino) == (descriptor.st_dev, descriptor.st_ino), sys.argv[1]
CHECK
}
exec 3< "${test_directory}/secrets"
exec 4< "${test_directory}/secrets/tls"
exec 5< "${test_directory}/secrets/webhook-encryption.bin"
sh "${test_directory}/setup.sh" --non-interactive --keep-env --keep-keys --secrets-directory secrets --no-tls > "${test_directory}/output"
same_inode "${test_directory}/secrets" 3 || { echo "Setup replaced the secrets directory." >&2; exit 1; }
same_inode "${test_directory}/secrets/tls" 4 || { echo "Setup replaced the TLS directory." >&2; exit 1; }
same_inode "${test_directory}/secrets/webhook-encryption.bin" 5 || { echo "Setup replaced a reused key." >&2; exit 1; }
cmp "${test_directory}/expected-webhook" "${test_directory}/secrets/webhook-encryption.bin"
sh "${test_directory}/setup.sh" --non-interactive --keep-env --replace-keys --secrets-directory secrets --no-tls > "${test_directory}/output"
same_inode "${test_directory}/secrets" 3
same_inode "${test_directory}/secrets/tls" 4
cmp "${test_directory}/expected-masterkey" "${test_directory}/secrets/zitadel-masterkey"
cmp "${test_directory}/expected-credential" "${test_directory}/secrets/external-secret.txt"
if cmp -s "${test_directory}/expected-webhook" "${test_directory}/secrets/webhook-encryption.bin"; then
    echo "Application key replacement did not replace its keys." >&2
    exit 1
fi
mkdir "${test_directory}/bin"
cat > "${test_directory}/bin/mkcert" <<'MOCK'
#!/bin/sh
openssl req -x509 -newkey rsa:2048 -nodes -subj /CN=localhost -days 1 -out "$2" -keyout "$4" >/dev/null 2>&1
MOCK
chmod +x "${test_directory}/bin/mkcert"
PATH="${test_directory}/bin:${PATH}" sh "${test_directory}/setup.sh" --non-interactive --keep-env --keep-keys --secrets-directory secrets --tls > "${test_directory}/output"
same_inode "${test_directory}/secrets/tls" 4
exec 6< "${test_directory}/secrets/tls/tls.crt"
sh "${test_directory}/setup.sh" --non-interactive --keep-env --keep-keys --secrets-directory secrets --no-tls > "${test_directory}/output"
same_inode "${test_directory}/secrets/tls/tls.crt" 6
PATH="${test_directory}/bin:${PATH}" sh "${test_directory}/setup.sh" --non-interactive --keep-env --keep-keys --secrets-directory secrets --tls > "${test_directory}/output"
same_inode "${test_directory}/secrets/tls" 4

cp -R "${test_directory}/secrets" "${test_directory}/expected-secrets"
cat > "${test_directory}/bin/mv" <<'MOCK'
#!/bin/sh
case "$*" in
    */.kairos-setup.*/secrets/vapid-private.pem*) exit 1 ;;
esac
exec /bin/mv "$@"
MOCK
chmod +x "${test_directory}/bin/mv"
if PATH="${test_directory}/bin:${PATH}" sh "${test_directory}/setup.sh" --non-interactive --keep-env --replace-keys --secrets-directory secrets --no-tls > "${test_directory}/output" 2>&1; then
    echo "Setup accepted a failed file installation." >&2
    exit 1
fi
diff -r "${test_directory}/expected-secrets" "${test_directory}/secrets"
same_inode "${test_directory}/secrets" 3
same_inode "${test_directory}/secrets/tls" 4
printf '%s\n' 'Setup generation, reuse, directory preservation, TLS replacement, and rollback passed.'

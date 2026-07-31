#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
tls_directory="${repository_directory}/secrets/tls"
certificate="${tls_directory}/tls.crt"
private_key="${tls_directory}/tls.key"

if [ -f "${certificate}" ] && [ -f "${private_key}" ]; then
    echo "Reusing the local HTTPS certificate in secrets/tls/."
    exit 0
fi

if [ -e "${certificate}" ] || [ -e "${private_key}" ]; then
    echo "Both secrets/tls/tls.crt and secrets/tls/tls.key must exist together." >&2
    exit 1
fi

if ! command -v mkcert >/dev/null 2>&1; then
    echo "mkcert is required to create the trusted local HTTPS certificate." >&2
    echo "Install mkcert, run 'mkcert -install', and then run ./setup.sh again." >&2
    exit 1
fi

mkdir -p "${tls_directory}"
mkcert \
    -cert-file "${certificate}" \
    -key-file "${private_key}" \
    customer.kairos.localhost \
    panel.kairos.localhost \
    api.kairos.localhost
chmod 0600 "${private_key}"
chmod 0644 "${certificate}"

echo "Generated the trusted local HTTPS certificate in secrets/tls/."

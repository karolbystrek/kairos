#!/bin/sh

set -eu

force=false

case "${1:-}" in
    "")
        ;;
    --force)
        force=true
        ;;
    *)
        echo "Usage: $0 [--force]" >&2
        exit 2
        ;;
esac

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
key_directory="${repository_directory}/secrets"
private_key="${key_directory}/jwt-private.pem"
public_key="${key_directory}/jwt-public.pem"

mkdir -p "${key_directory}"
umask 077

temporary_directory="$(mktemp -d "${key_directory}/.jwt-keys.XXXXXX")"
trap 'rm -rf "${temporary_directory}"' EXIT HUP INT TERM

derived_public_key="${temporary_directory}/derived-public.pem"
normalized_public_key="${temporary_directory}/normalized-public.pem"

if [ "${force}" = false ] && [ -f "${private_key}" ]; then
    openssl pkey -in "${private_key}" -check -noout >/dev/null
    openssl pkey -in "${private_key}" -pubout -out "${derived_public_key}"

    if [ ! -f "${public_key}" ]; then
        chmod 0644 "${derived_public_key}"
        mv "${derived_public_key}" "${public_key}"
        echo "Recovered secrets/jwt-public.pem from the existing private key."
        exit 0
    fi

    openssl pkey -pubin -in "${public_key}" -pubout -out "${normalized_public_key}"
    if ! cmp -s "${derived_public_key}" "${normalized_public_key}"; then
        echo "The JWT public and private keys in secrets/ do not form a pair." >&2
        exit 1
    fi

    echo "Reusing the JWT signing key pair in secrets/."
    exit 0
fi

if [ "${force}" = false ] && [ -f "${public_key}" ]; then
    echo "secrets/jwt-private.pem is missing while the public key exists." >&2
    exit 1
fi

generated_private_key="${temporary_directory}/jwt-private.pem"
generated_public_key="${temporary_directory}/jwt-public.pem"

openssl genpkey \
    -quiet \
    -algorithm RSA \
    -pkeyopt rsa_keygen_bits:3072 \
    -out "${generated_private_key}"
openssl pkey \
    -in "${generated_private_key}" \
    -pubout \
    -out "${generated_public_key}"

chmod 0600 "${generated_private_key}"
chmod 0644 "${generated_public_key}"
mv "${generated_private_key}" "${private_key}"
mv "${generated_public_key}" "${public_key}"

echo "Generated the JWT signing key pair in secrets/."

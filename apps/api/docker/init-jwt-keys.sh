#!/bin/sh

set -eu

key_directory="${KAIROS_JWT_KEY_DIRECTORY:-/run/secrets/kairos-jwt}"
private_key="${key_directory}/jwt-private.pem"
public_key="${key_directory}/jwt-public.pem"

mkdir -p "${key_directory}"
umask 077

temporary_directory="$(mktemp -d)"
trap 'rm -rf "${temporary_directory}"' EXIT HUP INT TERM

derived_public_key="${temporary_directory}/derived-public.pem"
normalized_public_key="${temporary_directory}/normalized-public.pem"

if [ -f "${private_key}" ]; then
    openssl pkey -in "${private_key}" -check -noout >/dev/null
    openssl pkey -in "${private_key}" -pubout -out "${derived_public_key}"

    if [ ! -f "${public_key}" ]; then
        chmod 0644 "${derived_public_key}"
        mv "${derived_public_key}" "${public_key}"
        echo "Recovered the missing local JWT public key from the persisted private key."
    else
        openssl pkey -pubin -in "${public_key}" -pubout -out "${normalized_public_key}"
        if ! cmp -s "${derived_public_key}" "${normalized_public_key}"; then
            echo "The persisted local JWT public and private keys do not form a pair." >&2
            exit 1
        fi

        echo "Reusing the persisted local JWT signing key pair."
    fi
elif [ -f "${public_key}" ]; then
    echo "The local JWT private key is missing while a public key is present." >&2
    exit 1
else
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

    echo "Generated and persisted a local JWT signing key pair."
fi

rm -rf "${temporary_directory}"
trap - EXIT HUP INT TERM

if [ "$#" -gt 0 ]; then
    exec "$@"
fi

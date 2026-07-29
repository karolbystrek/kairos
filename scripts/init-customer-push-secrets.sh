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
private_key="${key_directory}/vapid-private.pem"
public_key="${key_directory}/vapid-public.pem"
encryption_key="${key_directory}/push-subscription-encryption.bin"

mkdir -p "${key_directory}"
umask 077

if [ "${force}" = false ] && { [ -f "${private_key}" ] || [ -f "${public_key}" ]; }; then
    if [ ! -f "${private_key}" ] || [ ! -f "${public_key}" ]; then
        echo "Both secrets/vapid-private.pem and secrets/vapid-public.pem must exist together." >&2
        exit 1
    fi
    temporary_public_key="$(mktemp "${key_directory}/.vapid-public.XXXXXX")"
    trap 'rm -f "${temporary_public_key}"' EXIT HUP INT TERM
    openssl pkey -in "${private_key}" -pubout -out "${temporary_public_key}" >/dev/null 2>&1
    if ! cmp -s "${temporary_public_key}" "${public_key}"; then
        echo "The configured VAPID public and private keys do not form a pair." >&2
        exit 1
    fi
    rm -f "${temporary_public_key}"
    trap - EXIT HUP INT TERM
    echo "Reusing the VAPID P-256 key pair in secrets/."
else
    temporary_private_key="$(mktemp "${key_directory}/.vapid-private.XXXXXX")"
    temporary_public_key="$(mktemp "${key_directory}/.vapid-public.XXXXXX")"
    trap 'rm -f "${temporary_private_key}" "${temporary_public_key}"' EXIT HUP INT TERM
    openssl genpkey \
        -algorithm EC \
        -pkeyopt ec_paramgen_curve:P-256 \
        -out "${temporary_private_key}" >/dev/null 2>&1
    openssl pkey \
        -in "${temporary_private_key}" \
        -pubout \
        -out "${temporary_public_key}" >/dev/null 2>&1
    chmod 0600 "${temporary_private_key}" "${temporary_public_key}"
    mv "${temporary_private_key}" "${private_key}"
    mv "${temporary_public_key}" "${public_key}"
    echo "Generated a VAPID P-256 key pair for local development."
fi

if [ "${force}" = false ] && [ -f "${encryption_key}" ]; then
    key_size="$(wc -c < "${encryption_key}" | tr -d ' ')"
    if [ "${key_size}" -ne 32 ]; then
        echo "secrets/push-subscription-encryption.bin must contain exactly 32 bytes." >&2
        exit 1
    fi
    echo "Reusing the Push subscription encryption key in secrets/."
    exit 0
fi

temporary_encryption_key="$(mktemp "${key_directory}/.push-encryption.XXXXXX")"
trap 'rm -f "${temporary_encryption_key}"' EXIT HUP INT TERM
openssl rand -out "${temporary_encryption_key}" 32
chmod 0600 "${temporary_encryption_key}"
mv "${temporary_encryption_key}" "${encryption_key}"

echo "Generated secrets/push-subscription-encryption.bin for local development."

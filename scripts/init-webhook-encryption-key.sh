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
encryption_key="${key_directory}/webhook-encryption.bin"

mkdir -p "${key_directory}"
umask 077

if [ "${force}" = false ] && [ -f "${encryption_key}" ]; then
    key_size="$(wc -c < "${encryption_key}" | tr -d ' ')"
    if [ "${key_size}" -ne 32 ]; then
        echo "secrets/webhook-encryption.bin must contain exactly 32 bytes." >&2
        exit 1
    fi
    echo "Reusing the webhook signing-secret encryption key in secrets/."
    exit 0
fi

temporary_key="$(mktemp "${key_directory}/.webhook-encryption.XXXXXX")"
trap 'rm -f "${temporary_key}"' EXIT HUP INT TERM

openssl rand -out "${temporary_key}" 32
chmod 0600 "${temporary_key}"
mv "${temporary_key}" "${encryption_key}"

echo "Generated secrets/webhook-encryption.bin for local development."

#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ "$#" -ne 0 ]; then
    echo "Usage: $0" >&2
    exit 2
fi

echo
echo "Resetting Kairos containers, Compose volume data, and local environment..."
echo

cd "${repository_directory}"

if [ ! -f ".env.example" ]; then
    echo ".env.example is required to reset Kairos." >&2
    exit 1
fi

echo "Stopping Kairos and removing its containers and volumes..."
if [ -f ".env" ] || [ -L ".env" ]; then
    docker compose down -v --remove-orphans
else
    echo "No .env found; using .env.example for Docker Compose."
    docker compose --env-file .env.example down -v --remove-orphans
fi

if [ -f ".env" ] || [ -L ".env" ]; then
    mv -f ".env" ".env.old"
    echo "Saved the previous .env as .env.old."
fi
cp ".env.example" ".env"
chmod 0600 ".env"
echo "Copied .env.example to .env with mode 0600."

rm -f \
    "secrets/jwt-private.pem" \
    "secrets/jwt-public.pem" \
    "secrets/webhook-encryption.bin" \
    "secrets/vapid-private.pem" \
    "secrets/vapid-public.pem" \
    "secrets/push-subscription-encryption.bin" \
    "secrets/tls/tls.crt" \
    "secrets/tls/tls.key"
echo "Removed generated signing, encryption, and local TLS keys."

echo
echo "Reset complete. Run ./setup.sh to start Kairos."

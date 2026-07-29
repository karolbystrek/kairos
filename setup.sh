#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

echo "Setting up Kairos for local development..."

cp "${repository_directory}/.env.example" "${repository_directory}/.env"
echo "Copied .env.example to .env."

"${repository_directory}/scripts/init-jwt-keys.sh"
"${repository_directory}/scripts/init-webhook-encryption-key.sh"
"${repository_directory}/scripts/init-customer-push-secrets.sh"

echo
echo "Kairos dependencies are ready."
echo "Run 'docker compose up --build' to start the application."

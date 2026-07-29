#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
secret_directory="${repository_directory}/secrets"

confirm_overwrite() {
    description="$1"
    answer=""

    printf "%s already exists. Overwrite it? [y/N] " "${description}"
    IFS= read -r answer || answer=""

    case "${answer}" in
        y|Y|yes|YES|Yes)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

copy_environment_file() {
    environment_file="${repository_directory}/.env"

    if [ -e "${environment_file}" ]; then
        if ! confirm_overwrite ".env"; then
            echo "Keeping the existing .env file."
            return
        fi
    fi

    cp "${repository_directory}/.env.example" "${environment_file}"
    echo "Copied .env.example to .env."
}

run_initializer() {
    description="$1"
    initializer="$2"
    shift 2

    resource_exists=false
    for resource in "$@"; do
        if [ -e "${resource}" ]; then
            resource_exists=true
            break
        fi
    done

    if [ "${resource_exists}" = true ]; then
        if confirm_overwrite "${description}"; then
            "${initializer}" --force
            return
        fi
    fi

    "${initializer}"
}

echo "Setting up Kairos for local development..."

copy_environment_file
run_initializer \
    "JWT signing key pair" \
    "${repository_directory}/scripts/init-jwt-keys.sh" \
    "${secret_directory}/jwt-private.pem" \
    "${secret_directory}/jwt-public.pem"
run_initializer \
    "Webhook signing-secret encryption key" \
    "${repository_directory}/scripts/init-webhook-encryption-key.sh" \
    "${secret_directory}/webhook-encryption.bin"
run_initializer \
    "Customer Push secret set" \
    "${repository_directory}/scripts/init-customer-push-secrets.sh" \
    "${secret_directory}/vapid-private.pem" \
    "${secret_directory}/vapid-public.pem" \
    "${secret_directory}/push-subscription-encryption.bin"

echo
echo "Kairos is ready. Starting the application..."

cd "${repository_directory}"
exec docker compose up --build --watch

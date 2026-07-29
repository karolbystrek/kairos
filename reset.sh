#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
auto_confirm=false

if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [-y]" >&2
    exit 2
fi

case "${1:-}" in
    "")
        ;;
    -y)
        auto_confirm=true
        ;;
    *)
        echo "Usage: $0 [-y]" >&2
        exit 2
        ;;
esac

confirm() {
    prompt="$1"
    answer=""

    if [ "${auto_confirm}" = true ]; then
        return 0
    fi

    printf "%s [y/N] " "${prompt}"
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

remove_compose_data=false

echo
echo "WARNING: Reset can remove all Kairos containers and Compose volume data."
echo

cd "${repository_directory}"

if [ ! -f ".env.example" ]; then
    echo ".env.example is required to reset Kairos." >&2
    exit 1
fi

if confirm "Remove Kairos containers and all Compose volume data?"; then
    echo "Stopping Kairos and removing its containers and volumes..."
    if [ -f ".env" ] || [ -L ".env" ]; then
        docker compose down -v --remove-orphans
    else
        echo "No .env found; using .env.example for Docker Compose."
        docker compose --env-file .env.example down -v --remove-orphans
    fi
    remove_compose_data=true
else
    echo "Keeping Kairos containers and Compose volume data."
fi

if confirm "Reset .env from .env.example?"; then
    if [ -f ".env" ] || [ -L ".env" ]; then
        mv -f ".env" ".env.old"
        echo "Saved the previous .env as .env.old."
    fi
    cp ".env.example" ".env"
    echo "Copied .env.example to .env."
else
    echo "Keeping the existing .env file."
fi

if [ "${remove_compose_data}" = true ]; then
    echo "Regenerating local signing and encryption keys..."
    "${repository_directory}/scripts/init-jwt-keys.sh" --force
    "${repository_directory}/scripts/init-webhook-encryption-key.sh" --force
    "${repository_directory}/scripts/init-customer-push-secrets.sh" --force
else
    echo "Checking local signing and encryption keys..."
    "${repository_directory}/scripts/init-jwt-keys.sh"
    "${repository_directory}/scripts/init-webhook-encryption-key.sh"
    "${repository_directory}/scripts/init-customer-push-secrets.sh"
fi

echo
echo "Reset complete. Run ./setup.sh to start Kairos."

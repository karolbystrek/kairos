#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ "$#" -ne 0 ]; then
    echo "Usage: $0" >&2
    exit 2
fi

cd "${repository_directory}"

# .env.example supplies variables an older .env may lack; either file alone also works.
environment_files=""
configuration_files=""
for file in .env.example .env; do
    if [ -f "${file}" ] || [ -L "${file}" ]; then
        environment_files="${environment_files} --env-file ${file}"
        configuration_file="${file}"
        configuration_files="${configuration_files} ${file}"
    fi
done
[ -n "${environment_files}" ] || { echo ".env or .env.example is required to reset Kairos." >&2; exit 1; }

# .env is read last so it overrides .env.example, as in the --env-file order.
compose_file="$(cat ${configuration_files} | sed -n 's/^COMPOSE_FILE=//p' | tail -n 1)"
# COMPOSE_FILE exported in the environment overrides --env-file.
compose_file="${COMPOSE_FILE:-${compose_file}}"
case "${compose_file}" in
    *compose.production.yaml*)
        echo "Refusing to reset: COMPOSE_FILE selects production. reset.sh deletes volumes and keys; it only runs in development." >&2
        exit 1
        ;;
esac

secrets_directory="$(sed -n 's/^KAIROS_APPLICATION_SECRETS_DIRECTORY=//p' "${configuration_file}" | tail -n 1 | sed "s/^['\"]//;s/['\"]\$//")"
secrets_directory="${secrets_directory:-./secrets}"

echo
echo "Resetting Kairos containers, Compose volume data, and generated keys..."
echo

echo "Stopping Kairos and removing its containers and volumes..."
# shellcheck disable=SC2086 # Word splitting builds the --env-file arguments.
docker compose ${environment_files} down -v --remove-orphans

if [ -f ".env" ] || [ -L ".env" ]; then
    saved=".env.old"
    [ ! -e "${saved}" ] || saved=".env.old.$(date +%Y%m%d%H%M%S)"
    mv ".env" "${saved}"
    echo "Saved the previous .env as ${saved}."
fi

rm -f \
    "${secrets_directory}/webhook-encryption.bin" \
    "${secrets_directory}/vapid-private.pem" \
    "${secrets_directory}/vapid-public.pem" \
    "${secrets_directory}/push-subscription-encryption.bin" \
    "${secrets_directory}/email-encryption.bin"
echo "Removed generated keys from ${secrets_directory}."

echo
echo "Reset complete. Run ./setup.sh --dev (or --prod)."

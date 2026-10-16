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
for file in .env.example .env; do
    if [ -f "${file}" ] || [ -L "${file}" ]; then
        environment_files="${environment_files} --env-file ${file}"
        configuration_file="${file}"
    fi
done
[ -n "${environment_files}" ] || { echo ".env or .env.example is required to reset Kairos." >&2; exit 1; }

secrets_directory="$(sed -n 's/^KAIROS_APPLICATION_SECRETS_DIRECTORY=//p' "${configuration_file}" | tail -n 1)"
secrets_directory="${secrets_directory:-./secrets}"

echo
echo "Resetting Kairos containers, Compose volume data, and generated keys..."
echo

echo "Stopping Kairos and removing its containers and volumes..."
# shellcheck disable=SC2086 # Word splitting builds the --env-file arguments.
docker compose ${environment_files} down -v --remove-orphans

if [ -f ".env" ] || [ -L ".env" ]; then
    mv -f ".env" ".env.old"
    echo "Saved the previous .env as .env.old."
fi

rm -f \
    "${secrets_directory}/webhook-encryption.bin" \
    "${secrets_directory}/vapid-private.pem" \
    "${secrets_directory}/vapid-public.pem" \
    "${secrets_directory}/push-subscription-encryption.bin" \
    "${secrets_directory}/email-encryption.bin"
echo "Removed generated keys from ${secrets_directory}."

echo
echo "Reset complete. Run ./setup.sh --dev (or --prod), then docker compose up."

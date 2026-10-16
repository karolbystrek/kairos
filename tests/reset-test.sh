#!/bin/sh
set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
test_directory="$(mktemp -d "${TMPDIR:-/tmp}/kairos-reset-test.XXXXXX")"
trap 'rm -rf -- "${test_directory}"' EXIT HUP INT TERM
cp "${repository_directory}/reset.sh" "${test_directory}/"
mkdir "${test_directory}/bin"
export KAIROS_TEST_DOCKER="$(command -v docker)"
cat > "${test_directory}/.env.example" <<'ENV'
COMPOSE_FILE=compose.yaml
POSTGRES_USER=default-user
REDIS_PASSWORD=local-password
ENV
cat > "${test_directory}/.env" <<'ENV'
COMPOSE_FILE=compose.custom.yaml
COMPOSE_PROJECT_NAME=kairos-reset-test
POSTGRES_USER=existing-user
ENV
cp "${test_directory}/.env" "${test_directory}/expected-env"
cat > "${test_directory}/compose.custom.yaml" <<'YAML'
services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      REDIS_PASSWORD: ${REDIS_PASSWORD:?Set REDIS_PASSWORD}
YAML
# Substitute read-only Compose rendering for teardown; never contact the daemon.
cat > "${test_directory}/bin/docker" <<'PY'
#!/usr/bin/env python3
import json
import os
import subprocess
import sys

assert sys.argv[-3:] == ['down', '-v', '--remove-orphans']
result = subprocess.run([os.environ['KAIROS_TEST_DOCKER'], *sys.argv[1:-3], 'config', '--format', 'json'], capture_output=True, text=True)
assert result.returncode == 0, result.stderr
assert not result.stderr, result.stderr
config = json.loads(result.stdout)
assert config['name'] == 'kairos-reset-test'
environment = config['services']['postgres']['environment']
assert environment['POSTGRES_USER'] == 'existing-user'
assert environment['REDIS_PASSWORD'] == 'local-password'
PY
chmod +x "${test_directory}/bin/docker"
# Every Compose-mounted generated key must be removed.
mkdir -p "${test_directory}/secrets"
keys="$(sed -n 's|.*:-\./secrets}/||p' "${repository_directory}/compose.production.yaml")"
[ "$(echo "${keys}" | wc -w)" -ge 5 ]
for key in ${keys}; do echo fixture > "${test_directory}/secrets/${key}"; done
PATH="${test_directory}/bin:${PATH}" sh "${test_directory}/reset.sh" > "${test_directory}/output"
cmp "${test_directory}/expected-env" "${test_directory}/.env.old"
[ ! -e "${test_directory}/.env" ]
for key in ${keys}; do
    [ ! -e "${test_directory}/secrets/${key}" ] || { echo "reset.sh kept secrets/${key}" >&2; exit 1; }
done
# A production selection is refused before Docker or any file is touched.
echo 'COMPOSE_FILE="compose.yaml:compose.production.yaml"' > "${test_directory}/.env"
echo fixture > "${test_directory}/secrets/webhook-encryption.bin"
cat > "${test_directory}/bin/docker" <<'SH'
#!/bin/sh
echo "docker must not run for production" >&2
exit 99
SH
if PATH="${test_directory}/bin:${PATH}" sh "${test_directory}/reset.sh" > "${test_directory}/output" 2> "${test_directory}/error"; then
    echo "reset.sh ran against production" >&2; exit 1
fi
grep -q 'Refusing to reset' "${test_directory}/error"
[ -f "${test_directory}/.env" ] && [ -f "${test_directory}/secrets/webhook-encryption.bin" ]
printf '%s\n' 'Reset defaults, existing project configuration, key removal, environment backup and production refusal passed.'

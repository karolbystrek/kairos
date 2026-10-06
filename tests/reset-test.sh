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
ZITADEL_DB_USER=zitadel
ZITADEL_DB_PASSWORD=local-password
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
      ZITADEL_DB_USER: ${ZITADEL_DB_USER}
      ZITADEL_DB_PASSWORD: ${ZITADEL_DB_PASSWORD:?Set ZITADEL_DB_PASSWORD}
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
assert environment['ZITADEL_DB_USER'] == 'zitadel'
assert environment['ZITADEL_DB_PASSWORD'] == 'local-password'
PY
chmod +x "${test_directory}/bin/docker"
PATH="${test_directory}/bin:${PATH}" sh "${test_directory}/reset.sh" > "${test_directory}/output"
cmp "${test_directory}/expected-env" "${test_directory}/.env.old"
cmp "${test_directory}/.env.example" "${test_directory}/.env"
printf '%s\n' 'Reset defaults, existing project configuration, and environment backup passed.'

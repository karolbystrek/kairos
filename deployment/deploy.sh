#!/usr/bin/env bash
# Run from the extracted, exact-revision bundle on the production host.
set -euo pipefail
if [[ $# -ne 2 ]]; then
  echo 'Usage: deployment/deploy.sh RELEASE_DIRECTORY ENV_FILE' >&2
  exit 64
fi
exec python3 "$(dirname -- "${BASH_SOURCE[0]}")/deploy.py" "$@"

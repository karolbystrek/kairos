#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
environment_example="${repository_directory}/.env.example"
environment_file="${repository_directory}/.env"

preset=""
non_interactive=false
environment_action=""
key_action=""
secrets_argument=""

temporary_environment_file=""
working_directory=""
installed_files=""

usage() {
    cat <<EOF
Usage: $0 [--dev | --prod] [OPTIONS]

Choose --dev or --prod, or pass --non-interactive with explicit options.

Presets (explicit options below still take precedence):
  --dev                          Select and start the Docker development stack
                                 with hot reload, keeping existing config/keys.
  --prod                         Select and start the production Docker stack
                                 using production configuration. A copied
                                 setup command without Compose prepares keys only.

Options:
  --secrets-directory DIRECTORY  Store generated material in DIRECTORY.
  --replace-env                  Replace an existing .env from .env.example.
  --keep-env                     Preserve an existing .env.
  --replace-keys                 Replace an existing application key set.
  --keep-keys                    Reuse an existing complete, valid key set.
  --non-interactive              Run without a preset; every choice must be explicit.
  -h, --help                     Show this help.
EOF
}

fail() {
    echo "$1" >&2
    exit "${2:-1}"
}

set_action() {
    current_action="$1"
    requested_action="$2"
    option_family="$3"

    if [ -n "${current_action}" ] && [ "${current_action}" != "${requested_action}" ]; then
        fail "Conflicting ${option_family} options were provided." 2
    fi
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --secrets-directory)
            [ "$#" -ge 2 ] || fail "--secrets-directory requires a value." 2
            [ -z "${secrets_argument}" ] || fail "--secrets-directory was provided more than once." 2
            secrets_argument="$2"
            shift 2
            ;;
        --replace-env)
            set_action "${environment_action}" replace environment
            environment_action=replace
            shift
            ;;
        --keep-env)
            set_action "${environment_action}" keep environment
            environment_action=keep
            shift
            ;;
        --replace-keys)
            set_action "${key_action}" replace key
            key_action=replace
            shift
            ;;
        --keep-keys)
            set_action "${key_action}" keep key
            key_action=keep
            shift
            ;;
        --dev|--prod)
            [ -z "${preset}" ] || fail "Choose only one of --dev and --prod." 2
            preset="${1#--}"
            shift
            ;;
        --non-interactive)
            non_interactive=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            fail "Unknown option: $1" 2
            ;;
    esac
done

[ -n "${preset}" ] || [ "${non_interactive}" = true ] || { usage >&2; exit 2; }

# Write the directory into .env only when it was chosen now or .env had none; an existing value stays as written.
write_secrets_directory=false
[ -z "${secrets_argument}" ] || write_secrets_directory=true

checkout_mode=false
if [ -n "${preset}" ] && [ -f "${repository_directory}/compose.yaml" ]; then
    checkout_mode=true
fi

if [ "${checkout_mode}" = true ]; then
    # `config --environment` and `!reset` in compose.production.yaml need Compose 2.30.
    compose_version="$(docker compose version --short 2>/dev/null || true)"
    compose_version="${compose_version#v}"
    case "${compose_version}" in
        [0-9]*.[0-9]*) ;;
        *) fail "Docker Compose v2.30 or newer is required; \`docker compose version\` did not report a version." ;;
    esac
    compose_major="${compose_version%%.*}"
    compose_minor="${compose_version#*.}"
    compose_minor="${compose_minor%%.*}"
    if [ "${compose_major}" -lt 2 ] || { [ "${compose_major}" -eq 2 ] && [ "${compose_minor%%[!0-9]*}" -lt 30 ]; }; then
        fail "Docker Compose v2.30 or newer is required; found ${compose_version}."
    fi
fi

case "${preset}" in
    dev)
        : "${environment_action:=keep}"
        : "${key_action:=keep}"
        ;;
    prod)
        : "${key_action:=keep}"
        if [ "${checkout_mode}" = true ]; then
            : "${environment_action:=keep}"
            environment_example="${repository_directory}/deployment/production.env.example"
        else
            : "${environment_action:=skip}"
            : "${secrets_argument:=/srv/kairos/secrets}"
        fi
        ;;
esac

if [ -n "${preset}" ] && [ -z "${secrets_argument}" ]; then
    if [ -f "${environment_file}" ]; then
        secrets_argument="$(sed -n 's/^KAIROS_APPLICATION_SECRETS_DIRECTORY=//p' "${environment_file}" | tail -n 1 | sed "s/^['\"]//;s/['\"]$//")"
    fi
    [ -n "${secrets_argument}" ] || write_secrets_directory=true
    : "${secrets_argument:=secrets}"
fi

cleanup() {
    if [ -n "${temporary_environment_file}" ] && [ -f "${temporary_environment_file}" ]; then
        rm -f -- "${temporary_environment_file}"
    fi
    rollback_failed=false
    for installed_file in ${installed_files}; do
        if [ -f "${working_directory}/backup/${installed_file}" ]; then
            if ! mv -f "${working_directory}/backup/${installed_file}" "${secrets_directory}/${installed_file}"; then
                rollback_failed=true
            fi
        elif ! rm -f "${secrets_directory}/${installed_file}"; then
            rollback_failed=true
        fi
    done
    if [ "${rollback_failed}" = true ]; then
        echo "Could not restore all previous secrets; backups remain at ${working_directory}." >&2
    elif [ -n "${working_directory}" ] && [ -d "${working_directory}" ]; then
        rm -rf -- "${working_directory}"
    fi
}

trap cleanup EXIT HUP INT TERM
umask 077

write_environment_file() {
    [ -f "${environment_example}" ] || fail "The selected configuration template is missing: ${environment_example}"

    temporary_environment_file="$(mktemp "${repository_directory}/.env.tmp.XXXXXX")"
    cp "${environment_example}" "${temporary_environment_file}"
    chmod 0600 "${temporary_environment_file}"
    mv -f "${temporary_environment_file}" "${environment_file}"
    temporary_environment_file=""
}

if [ "${environment_action}" = skip ]; then
    echo "Skipped .env; deployment writes production.env."
elif [ -e "${environment_file}" ] || [ -L "${environment_file}" ]; then
    if [ ! -f "${environment_file}" ] && [ ! -L "${environment_file}" ]; then
        fail ".env exists but is not a file."
    fi

    [ -n "${environment_action}" ] || fail "An existing .env requires --replace-env or --keep-env in non-interactive mode."

    if [ "${environment_action}" = replace ]; then
        write_environment_file
        echo "Replaced .env from the selected configuration template with mode 0600."
    else
        if [ -f "${environment_file}" ] && [ ! -L "${environment_file}" ]; then
            chmod 0600 "${environment_file}"
        fi
        echo "Kept the existing .env."
    fi
else
    write_environment_file
    echo "Created .env from the selected configuration template with mode 0600."
fi

set_environment_value() {
    temporary_environment_file="$(mktemp "${repository_directory}/.env.tmp.XXXXXX")"
    # ENVIRON, not -v, so backslashes in the value stay literal.
    KEY="$1" VALUE="$2" awk '
        index($0, ENVIRON["KEY"] "=") == 1 { if (!written++) print ENVIRON["KEY"] "=" ENVIRON["VALUE"]; next }
        { print }
        END { if (!written) print ENVIRON["KEY"] "=" ENVIRON["VALUE"] }
    ' "${environment_file}" > "${temporary_environment_file}"
    mv -f "${temporary_environment_file}" "${environment_file}"
    temporary_environment_file=""
}

if [ "${checkout_mode}" = true ]; then
    # Add new settings without replacing credentials or personal configuration.
    temporary_environment_file="$(mktemp "${repository_directory}/.env.tmp.XXXXXX")"
    awk '
        NR == FNR { split($0, fields, "="); present[fields[1]]=1; print; next }
        /^[A-Z][A-Z0-9_]*=/ { split($0, fields, "="); if (!present[fields[1]]) print }
    ' "${environment_file}" "${environment_example}" > "${temporary_environment_file}"
    mv -f "${temporary_environment_file}" "${environment_file}"
    temporary_environment_file=""
    if [ "${preset}" = dev ]; then
        set_environment_value COMPOSE_FILE compose.yaml:compose.local.yaml
    else
        set_environment_value COMPOSE_FILE compose.yaml:compose.production.yaml
        set_environment_value KAIROS_API_USER "$(id -u):$(id -g)"
        for setting in KAIROS_IMAGE_REGISTRY=kairos-local KAIROS_RELEASE_VERSION=production; do
            key="${setting%%=*}"
            if ! grep -Eq "^${key}=.+" "${environment_file}"; then
                set_environment_value "${key}" "${setting#*=}"
            fi
        done
    fi
    set_environment_value POSTGRES_HOST postgres
    set_environment_value REDIS_HOST redis
    set_environment_value KAIROS_SECRETS_DIRECTORY /run/secrets
fi

[ -n "${secrets_argument}" ] || fail "--secrets-directory is required in non-interactive mode."

case "${secrets_argument}" in
    /*)
        secrets_path="${secrets_argument}"
        ;;
    *)
        secrets_path="${repository_directory}/${secrets_argument}"
        ;;
esac

if [ -L "${secrets_path}" ]; then
    fail "The secrets directory must not be a symbolic link."
fi

if [ -e "${secrets_path}" ]; then
    [ -d "${secrets_path}" ] || fail "The secrets path exists but is not a directory."
    secrets_directory="$(CDPATH= cd -- "${secrets_path}" && pwd -P)"
    secrets_parent="$(dirname -- "${secrets_directory}")"
else
    secrets_name="$(basename -- "${secrets_path}")"
    secrets_parent_argument="$(dirname -- "${secrets_path}")"
    if ! secrets_parent="$(CDPATH= cd -- "${secrets_parent_argument}" && pwd -P)"; then
        fail "The parent of the secrets directory must already exist."
    fi
    secrets_directory="${secrets_parent}/${secrets_name}"
fi

[ "${secrets_directory}" != "/" ] || fail "The filesystem root cannot be used as the secrets directory."

case "${secrets_directory}/" in
    "${repository_directory}/"*)
        if [ "${secrets_directory}" != "${repository_directory}/secrets" ]; then
            fail "Inside the checkout, setup may write key material only to the Git-ignored secrets/ directory."
        fi
        ;;
esac

command -v openssl >/dev/null 2>&1 || fail "OpenSSL is required to generate and validate application keys."

working_directory="$(mktemp -d "${secrets_parent}/.kairos-setup.XXXXXX")"
chmod 0700 "${working_directory}"

key_names="
webhook-encryption.bin
email-encryption.bin
vapid-private.pem
vapid-public.pem
push-subscription-encryption.bin
"

is_key_name() {
    candidate_name="$1"
    for expected_name in ${key_names}; do
        if [ "${candidate_name}" = "${expected_name}" ]; then
            return 0
        fi
    done
    return 1
}

existing_key_count=0

if [ -d "${secrets_directory}" ]; then
    for entry in \
        "${secrets_directory}"/* \
        "${secrets_directory}"/.[!.]* \
        "${secrets_directory}"/..?*; do
        if [ ! -e "${entry}" ] && [ ! -L "${entry}" ]; then
            continue
        fi

        entry_name="$(basename -- "${entry}")"
        if is_key_name "${entry_name}"; then
            if [ -L "${entry}" ] || [ ! -f "${entry}" ]; then
                fail "The secrets entry is not a regular file: ${entry_name}"
            fi
            existing_key_count=$((existing_key_count + 1))
        elif [ -f "${entry}" ] && [ ! -L "${entry}" ]; then
            : # Externally managed files are preserved; setup does not own them.
        elif [ "${entry_name}" = tls ]; then
            # The local TLS directory stays untouched.
            if [ -L "${entry}" ] || [ ! -d "${entry}" ]; then
                fail "The secrets entry must be a regular directory: ${entry_name}"
            fi
        else
            fail "The secrets directory contains an unrelated entry: ${entry_name}"
        fi
    done
fi

validate_key_set() {
    key_directory="$1"
    validation_directory="${working_directory}/validation"
    mkdir -p "${validation_directory}"

    openssl pkey \
        -in "${key_directory}/vapid-private.pem" \
        -check \
        -noout \
        >/dev/null 2>&1 || return 1
    openssl pkey \
        -in "${key_directory}/vapid-private.pem" \
        -pubout \
        -out "${validation_directory}/vapid-derived.pem" \
        >/dev/null 2>&1 || return 1
    openssl pkey \
        -pubin \
        -in "${key_directory}/vapid-public.pem" \
        -pubout \
        -out "${validation_directory}/vapid-normalized.pem" \
        >/dev/null 2>&1 || return 1
    cmp -s \
        "${validation_directory}/vapid-derived.pem" \
        "${validation_directory}/vapid-normalized.pem" || return 1
    openssl pkey \
        -pubin \
        -in "${key_directory}/vapid-public.pem" \
        -text \
        -noout \
        > "${validation_directory}/vapid-details.txt" \
        2>/dev/null || return 1
    grep -Eq 'ASN1 OID: prime256v1|NIST CURVE: P-256' \
        "${validation_directory}/vapid-details.txt" || return 1

    for encryption_key in \
        "${key_directory}/webhook-encryption.bin" \
        "${key_directory}/email-encryption.bin" \
        "${key_directory}/push-subscription-encryption.bin"; do
        key_size="$(wc -c < "${encryption_key}" | tr -d ' ')" || return 1
        [ "${key_size}" -eq 32 ] || return 1
    done

    return 0
}

key_set_status=absent
if [ "${existing_key_count}" -eq 5 ]; then
    if validate_key_set "${secrets_directory}"; then
        key_set_status=valid
    else
        key_set_status=invalid
    fi
elif [ "${existing_key_count}" -ne 0 ]; then
    key_set_status=partial
fi

case "${key_action}" in
    replace)
        final_key_action=generate
        ;;
    keep)
        case "${key_set_status}" in
            absent)
                final_key_action=generate
                ;;
            valid)
                final_key_action=reuse
                ;;
            partial)
                fail "Cannot keep a partial application key set (${existing_key_count} of 5 files)."
                ;;
            invalid)
                fail "Cannot keep the existing invalid application key set."
                ;;
        esac
        ;;
    "")
        case "${key_set_status}" in
            absent)
                final_key_action=generate
                ;;
            valid)
                fail "Existing application keys require --replace-keys or --keep-keys in non-interactive mode."
                ;;
            partial)
                fail "A partial application key set requires --replace-keys in non-interactive mode."
                ;;
            invalid)
                fail "An invalid application key set requires --replace-keys in non-interactive mode."
                ;;
        esac
        ;;
esac

new_secrets_directory="${working_directory}/secrets"
mkdir "${new_secrets_directory}"
chmod 0700 "${new_secrets_directory}"

if [ "${final_key_action}" = generate ]; then
    openssl rand \
        -out "${new_secrets_directory}/webhook-encryption.bin" \
        32
    openssl rand -out "${new_secrets_directory}/email-encryption.bin" 32
    openssl genpkey \
        -algorithm EC \
        -pkeyopt ec_paramgen_curve:P-256 \
        -out "${new_secrets_directory}/vapid-private.pem" \
        >/dev/null 2>&1
    openssl pkey \
        -in "${new_secrets_directory}/vapid-private.pem" \
        -pubout \
        -out "${new_secrets_directory}/vapid-public.pem" \
        >/dev/null 2>&1
    openssl rand \
        -out "${new_secrets_directory}/push-subscription-encryption.bin" \
        32
else
    for key_name in ${key_names}; do
        cp "${secrets_directory}/${key_name}" "${new_secrets_directory}/${key_name}"
    done
fi

for key_name in ${key_names}; do
    chmod 0400 "${new_secrets_directory}/${key_name}"
done

validate_key_set "${new_secrets_directory}" || fail "The prepared application key set failed validation."

# Keep directory identities stable for Docker Desktop bind mounts.
mkdir -p "${secrets_directory}"
chmod 0700 "${secrets_directory}"
replacement_files=""
if [ "${final_key_action}" = generate ]; then
    for key_name in ${key_names}; do
        replacement_files="${replacement_files} ${key_name}"
    done
fi

mkdir -p "${working_directory}/backup"
for replacement_file in ${replacement_files}; do
    if [ -f "${secrets_directory}/${replacement_file}" ]; then
        cp -p "${secrets_directory}/${replacement_file}" "${working_directory}/backup/${replacement_file}"
    fi
done
for replacement_file in ${replacement_files}; do
    installed_files="${replacement_file} ${installed_files}"
    if ! mv -f "${new_secrets_directory}/${replacement_file}" "${secrets_directory}/${replacement_file}"; then
        fail "Could not install ${replacement_file}; restoring previous secrets."
    fi
done
installed_files=""

rm -rf -- "${working_directory}"
working_directory=""

if [ "${final_key_action}" = generate ]; then
    echo "Generated and validated the complete application key set in ${secrets_directory}."
else
    echo "Reused and validated the complete application key set in ${secrets_directory}."
fi


if [ "${checkout_mode}" = true ]; then
    if [ "${write_secrets_directory}" = true ]; then
        set_environment_value KAIROS_APPLICATION_SECRETS_DIRECTORY "${secrets_directory}"
    fi
    docker compose --project-directory "${repository_directory}" config --quiet ||
        fail "The ${preset} stack is selected but configuration is incomplete. Update .env and TLS paths, then retry --${preset}; existing values and keys were preserved."
    if [ "${preset}" = prod ]; then
        # Preserved development settings must never relax production boundaries.
        docker compose --project-directory "${repository_directory}" config --environment | awk -F= '
            FNR == NR { if ($1 ~ /^(POSTGRES_PASSWORD|KAIROS_DB_OWNER_PASSWORD|KAIROS_DB_RUNTIME_PASSWORD|REDIS_PASSWORD)$/) example[$1]=$2; next }
            $1 ~ /^(WEBHOOK_DESTINATION_POLICY|PUSH_DESTINATION_POLICY)$/ && $2 != "PUBLIC_HTTPS" { invalid=1 }
            $1 == "EMAIL_VERIFICATION_ENABLED" && tolower($2) != "true" { invalid=1 }
            $1 ~ /^(CUSTOMER_APP_URL|PANEL_APP_URL)$/ && $2 !~ "^https://[^/]+/?$" { invalid=1 }
            $1 ~ /^(POSTGRES_PASSWORD|KAIROS_DB_OWNER_PASSWORD|KAIROS_DB_RUNTIME_PASSWORD|REDIS_PASSWORD)$/ { if ($2 == "" || $2 == example[$1]) invalid=1 }
            END { exit invalid }
        ' "${repository_directory}/.env.example" - || fail "Production requires HTTPS origins, PUBLIC_HTTPS delivery policies, email verification and non-default database and Redis passwords. Update .env, then retry --prod; existing settings were preserved."
    fi
    docker compose --project-directory "${repository_directory}" up --build -d --wait
    echo
    echo "The ${preset} Docker stack is running. Rebuild: docker compose up --build -d"
    echo "Stop and keep data: docker compose stop"
fi
trap - EXIT HUP INT TERM

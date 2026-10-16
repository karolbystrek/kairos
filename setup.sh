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

Without a preset, setup asks about each step.

Presets (explicit options below still take precedence):
  --dev                          Local development: keep or create .env from
                                 .env.example and keep or create keys in secrets/,
                                 install frontend dependencies and start the
                                 PostgreSQL and Redis containers.
  --prod                         Production host: keep or create keys in
                                 /srv/kairos/secrets; deployment owns production.env.

Options:
  --secrets-directory DIRECTORY  Store generated material in DIRECTORY.
  --replace-env                  Replace an existing .env from .env.example.
  --keep-env                     Preserve an existing .env.
  --replace-keys                 Replace an existing application key set.
  --keep-keys                    Reuse an existing complete, valid key set.
  --non-interactive              Fail instead of prompting for missing choices.
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

case "${preset}" in
    dev)
        non_interactive=true
        : "${environment_action:=keep}"
        : "${key_action:=keep}"
        : "${secrets_argument:=secrets}"
        ;;
    prod)
        non_interactive=true
        : "${environment_action:=skip}"
        : "${key_action:=keep}"
        : "${secrets_argument:=/srv/kairos/secrets}"
        ;;
esac

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

prompt_yes_no() {
    prompt_text="$1"
    default_answer="$2"

    while :; do
        if [ "${default_answer}" = yes ]; then
            printf '%s [Y/n] ' "${prompt_text}"
        else
            printf '%s [y/N] ' "${prompt_text}"
        fi

        if ! IFS= read -r entered_answer; then
            fail "Input ended before setup received an answer."
        fi

        normalized_answer="$(printf '%s' "${entered_answer}" | tr '[:upper:]' '[:lower:]')"
        case "${normalized_answer}" in
            "")
                prompt_result="${default_answer}"
                return
                ;;
            y|yes)
                prompt_result=yes
                return
                ;;
            n|no)
                prompt_result=no
                return
                ;;
            *)
                echo "Enter yes or no." >&2
                ;;
        esac
    done
}

write_environment_file() {
    [ -f "${environment_example}" ] || fail ".env.example is required for setup."

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

    if [ -z "${environment_action}" ]; then
        if [ "${non_interactive}" = true ]; then
            fail "An existing .env requires --replace-env or --keep-env in non-interactive mode."
        fi
        prompt_yes_no "Replace .env from .env.example?" yes
        if [ "${prompt_result}" = yes ]; then
            environment_action=replace
        else
            environment_action=keep
        fi
    fi

    if [ "${environment_action}" = replace ]; then
        write_environment_file
        echo "Replaced .env from .env.example with mode 0600."
    else
        if [ -f "${environment_file}" ] && [ ! -L "${environment_file}" ]; then
            chmod 0600 "${environment_file}"
        fi
        echo "Kept the existing .env."
    fi
else
    write_environment_file
    echo "Created .env from .env.example with mode 0600."
fi

if [ -z "${secrets_argument}" ]; then
    if [ "${non_interactive}" = true ]; then
        fail "--secrets-directory is required in non-interactive mode."
    fi

    printf 'Secrets directory [secrets/]: '
    if ! IFS= read -r secrets_argument; then
        fail "Input ended before setup received a secrets directory."
    fi
    if [ -z "${secrets_argument}" ]; then
        secrets_argument="secrets"
    fi
fi

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
        elif [ "${entry_name}" = zitadel ] || [ "${entry_name}" = tls ]; then
            # Legacy ZITADEL credential and local TLS directories from older setups stay untouched.
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
                if [ "${non_interactive}" = true ]; then
                    fail "Existing application keys require --replace-keys or --keep-keys in non-interactive mode."
                fi
                prompt_yes_no "Replace the existing application key set?" no
                if [ "${prompt_result}" = yes ]; then
                    final_key_action=generate
                else
                    final_key_action=reuse
                fi
                ;;
            partial)
                if [ "${non_interactive}" = true ]; then
                    fail "A partial application key set requires --replace-keys in non-interactive mode."
                fi
                prompt_yes_no "Replace the partial application key set (${existing_key_count} of 5 files)?" no
                [ "${prompt_result}" = yes ] || fail "Setup stopped without replacing the partial application key set."
                final_key_action=generate
                ;;
            invalid)
                if [ "${non_interactive}" = true ]; then
                    fail "An invalid application key set requires --replace-keys in non-interactive mode."
                fi
                prompt_yes_no "Replace the existing invalid application key set?" no
                [ "${prompt_result}" = yes ] || fail "Setup stopped without replacing the invalid application key set."
                final_key_action=generate
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


trap - EXIT HUP INT TERM

# A local checkout also gets frontend dependencies and running infrastructure; copies without Compose (tests) skip this.
if [ "${preset}" = dev ] && [ -f "${repository_directory}/compose.yaml" ]; then
    for app in customer-app panel-app; do
        [ -d "${repository_directory}/apps/${app}/node_modules" ] || npm --prefix "${repository_directory}/apps/${app}" ci
    done
    docker compose --project-directory "${repository_directory}" up -d --wait
    echo
    echo "Infrastructure is running. Start the applications with hot reload:"
    echo "  (cd apps/api && ./mvnw spring-boot:run)   # http://localhost:8080/api"
    echo "  npm --prefix apps/customer-app run dev     # http://localhost:3000"
    echo "  npm --prefix apps/panel-app run dev        # http://localhost:3001"
fi

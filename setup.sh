#!/bin/sh

set -eu

repository_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
environment_example="${repository_directory}/.env.example"
environment_file="${repository_directory}/.env"

non_interactive=false
environment_action=""
key_action=""
tls_action=""
secrets_argument=""

temporary_environment_file=""
working_directory=""
backup_directory=""

usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Options:
  --secrets-directory DIRECTORY  Store generated material in DIRECTORY.
  --replace-env                  Replace an existing .env from .env.example.
  --keep-env                     Preserve an existing .env.
  --replace-keys                 Replace an existing application key set.
  --keep-keys                    Reuse an existing complete, valid key set.
  --tls                          Generate or replace the local TLS certificate.
  --no-tls                       Do not generate a TLS certificate.
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
        --tls)
            set_action "${tls_action}" generate TLS
            tls_action=generate
            shift
            ;;
        --no-tls)
            set_action "${tls_action}" skip TLS
            tls_action=skip
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

cleanup() {
    if [ -n "${temporary_environment_file}" ] && [ -f "${temporary_environment_file}" ]; then
        rm -f -- "${temporary_environment_file}"
    fi
    if [ -n "${working_directory}" ] && [ -d "${working_directory}" ]; then
        rm -rf -- "${working_directory}"
    fi
    if [ -n "${backup_directory}" ] && [ -d "${backup_directory}" ]; then
        if [ ! -e "${secrets_directory}" ]; then
            if ! mv "${backup_directory}" "${secrets_directory}"; then
                echo "The previous secrets directory remains at ${backup_directory}." >&2
            fi
        else
            echo "The previous secrets directory remains at ${backup_directory}." >&2
        fi
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

if [ -e "${environment_file}" ] || [ -L "${environment_file}" ]; then
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
jwt-private.pem
jwt-public.pem
webhook-encryption.bin
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
existing_tls_count=0

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
        elif [ "${entry_name}" = tls ]; then
            if [ -L "${entry}" ] || [ ! -d "${entry}" ]; then
                fail "The TLS secrets entry must be a regular directory."
            fi
        else
            fail "The secrets directory contains an unrelated entry: ${entry_name}"
        fi
    done
fi

tls_directory="${secrets_directory}/tls"
if [ -d "${tls_directory}" ]; then
    for entry in \
        "${tls_directory}"/* \
        "${tls_directory}"/.[!.]* \
        "${tls_directory}"/..?*; do
        if [ ! -e "${entry}" ] && [ ! -L "${entry}" ]; then
            continue
        fi

        entry_name="$(basename -- "${entry}")"
        case "${entry_name}" in
            tls.crt|tls.key)
                if [ -L "${entry}" ] || [ ! -f "${entry}" ]; then
                    fail "The TLS secrets entry is not a regular file: ${entry_name}"
                fi
                existing_tls_count=$((existing_tls_count + 1))
                ;;
            *)
                fail "The TLS secrets directory contains an unrelated entry: ${entry_name}"
                ;;
        esac
    done
fi

validate_key_set() {
    key_directory="$1"
    validation_directory="${working_directory}/validation"
    mkdir -p "${validation_directory}"

    openssl pkey \
        -in "${key_directory}/jwt-private.pem" \
        -check \
        -noout \
        >/dev/null 2>&1 || return 1
    openssl pkey \
        -in "${key_directory}/jwt-private.pem" \
        -pubout \
        -out "${validation_directory}/jwt-derived.pem" \
        >/dev/null 2>&1 || return 1
    openssl pkey \
        -pubin \
        -in "${key_directory}/jwt-public.pem" \
        -pubout \
        -out "${validation_directory}/jwt-normalized.pem" \
        >/dev/null 2>&1 || return 1
    cmp -s \
        "${validation_directory}/jwt-derived.pem" \
        "${validation_directory}/jwt-normalized.pem" || return 1
    openssl pkey \
        -pubin \
        -in "${key_directory}/jwt-public.pem" \
        -text \
        -noout \
        > "${validation_directory}/jwt-details.txt" \
        2>/dev/null || return 1
    grep -Eq 'Public-Key: \(3072 bit\)' \
        "${validation_directory}/jwt-details.txt" || return 1

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
        "${key_directory}/push-subscription-encryption.bin"; do
        key_size="$(wc -c < "${encryption_key}" | tr -d ' ')" || return 1
        [ "${key_size}" -eq 32 ] || return 1
    done

    return 0
}

validate_tls_pair() {
    certificate="$1"
    private_key="$2"
    validation_directory="${working_directory}/validation"
    mkdir -p "${validation_directory}"

    openssl x509 \
        -in "${certificate}" \
        -pubkey \
        -noout \
        > "${validation_directory}/tls-certificate-public.pem" \
        2>/dev/null || return 1
    openssl pkey \
        -in "${private_key}" \
        -pubout \
        -out "${validation_directory}/tls-private-public.pem" \
        >/dev/null 2>&1 || return 1
    cmp -s \
        "${validation_directory}/tls-certificate-public.pem" \
        "${validation_directory}/tls-private-public.pem" || return 1
}

key_set_status=absent
if [ "${existing_key_count}" -eq 6 ]; then
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
                fail "Cannot keep a partial application key set (${existing_key_count} of 6 files)."
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
                prompt_yes_no "Replace the partial application key set (${existing_key_count} of 6 files)?" no
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

if [ -z "${tls_action}" ]; then
    if [ "${non_interactive}" = true ]; then
        fail "--tls or --no-tls is required in non-interactive mode."
    fi
    prompt_yes_no "Generate or replace the TLS certificate?" yes
    if [ "${prompt_result}" = yes ]; then
        tls_action=generate
    else
        tls_action=skip
    fi
fi

existing_tls_status=absent
if [ "${existing_tls_count}" -eq 2 ]; then
    if validate_tls_pair "${tls_directory}/tls.crt" "${tls_directory}/tls.key"; then
        existing_tls_status=valid
    else
        existing_tls_status=invalid
    fi
elif [ "${existing_tls_count}" -ne 0 ]; then
    existing_tls_status=partial
fi

if [ "${tls_action}" = skip ]; then
    case "${existing_tls_status}" in
        partial)
            fail "Cannot preserve a partial TLS key pair."
            ;;
        invalid)
            fail "Cannot preserve the existing invalid TLS key pair."
            ;;
    esac
fi

new_secrets_directory="${working_directory}/secrets"
mkdir "${new_secrets_directory}"
chmod 0700 "${new_secrets_directory}"

if [ "${final_key_action}" = generate ]; then
    openssl genpkey \
        -quiet \
        -algorithm RSA \
        -pkeyopt rsa_keygen_bits:3072 \
        -out "${new_secrets_directory}/jwt-private.pem"
    openssl pkey \
        -in "${new_secrets_directory}/jwt-private.pem" \
        -pubout \
        -out "${new_secrets_directory}/jwt-public.pem"
    openssl rand \
        -out "${new_secrets_directory}/webhook-encryption.bin" \
        32
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

if [ "${tls_action}" = generate ]; then
    command -v mkcert >/dev/null 2>&1 || fail "mkcert is required when TLS generation is enabled."

    new_tls_directory="${new_secrets_directory}/tls"
    mkdir "${new_tls_directory}"
    chmod 0700 "${new_tls_directory}"
    mkcert_output="${working_directory}/mkcert-output.txt"
    if ! mkcert \
        -cert-file "${new_tls_directory}/tls.crt" \
        -key-file "${new_tls_directory}/tls.key" \
        customer.kairos.localhost \
        panel.kairos.localhost \
        api.kairos.localhost \
        > "${mkcert_output}" \
        2>&1; then
        cat "${mkcert_output}" >&2
        fail "mkcert could not generate the TLS certificate."
    fi
    chmod 0644 "${new_tls_directory}/tls.crt"
    chmod 0600 "${new_tls_directory}/tls.key"
    validate_tls_pair \
        "${new_tls_directory}/tls.crt" \
        "${new_tls_directory}/tls.key" || fail "The generated TLS certificate and private key do not form a pair."
elif [ "${existing_tls_status}" = valid ]; then
    new_tls_directory="${new_secrets_directory}/tls"
    mkdir "${new_tls_directory}"
    chmod 0700 "${new_tls_directory}"
    cp "${tls_directory}/tls.crt" "${new_tls_directory}/tls.crt"
    cp "${tls_directory}/tls.key" "${new_tls_directory}/tls.key"
    chmod 0644 "${new_tls_directory}/tls.crt"
    chmod 0600 "${new_tls_directory}/tls.key"
    validate_tls_pair \
        "${new_tls_directory}/tls.crt" \
        "${new_tls_directory}/tls.key" || fail "The preserved TLS certificate and private key failed validation."
fi

if [ -d "${secrets_directory}" ]; then
    backup_directory="$(mktemp -d "${secrets_parent}/.kairos-setup.backup.XXXXXX")"
    rmdir "${backup_directory}"
    if ! mv "${secrets_directory}" "${backup_directory}"; then
        fail "Could not move the existing secrets directory aside."
    fi
    if ! mv "${new_secrets_directory}" "${secrets_directory}"; then
        if ! mv "${backup_directory}" "${secrets_directory}"; then
            echo "Could not restore the previous secrets directory from ${backup_directory}." >&2
        fi
        fail "Could not install the prepared secrets directory."
    fi
    rm -rf -- "${backup_directory}"
    backup_directory=""
else
    mv "${new_secrets_directory}" "${secrets_directory}"
fi

rm -rf -- "${working_directory}"
working_directory=""

if [ "${final_key_action}" = generate ]; then
    echo "Generated and validated the complete application key set in ${secrets_directory}."
else
    echo "Reused and validated the complete application key set in ${secrets_directory}."
fi

case "${tls_action}:${existing_tls_status}" in
    generate:*)
        echo "Generated and validated the TLS certificate in ${secrets_directory}/tls."
        ;;
    skip:valid)
        echo "Preserved and validated the TLS certificate in ${secrets_directory}/tls."
        ;;
    skip:*)
        echo "Skipped TLS certificate generation."
        ;;
esac

trap - EXIT HUP INT TERM

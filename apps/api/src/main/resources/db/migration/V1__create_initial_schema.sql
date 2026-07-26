CREATE TABLE tenants
(
    id UUID PRIMARY KEY
);

CREATE TABLE locations
(
    id        UUID PRIMARY KEY,
    tenant_id UUID        NOT NULL REFERENCES tenants (id),
    time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    CONSTRAINT locations_time_zone_not_blank_check CHECK (TRIM(time_zone) <> ''),
    CONSTRAINT locations_id_tenant_key UNIQUE (id, tenant_id)
);

CREATE INDEX locations_tenant_id_idx ON locations (tenant_id);

CREATE TABLE external_integrations
(
    id              UUID PRIMARY KEY,
    tenant_id       UUID                     NOT NULL REFERENCES tenants (id),
    name            VARCHAR(64)              NOT NULL,
    normalized_name VARCHAR(128)             NOT NULL,
    status          VARCHAR(32)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_enabled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT external_integrations_name_not_blank_check CHECK (TRIM(name) <> ''),
    CONSTRAINT external_integrations_name_stripped_check CHECK (name = TRIM(name)),
    CONSTRAINT external_integrations_normalized_name_not_blank_check CHECK (TRIM(normalized_name) <> ''),
    CONSTRAINT external_integrations_status_check CHECK (status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT external_integrations_archive_check CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
            OR (status <> 'ARCHIVED' AND archived_at IS NULL)
        ),
    CONSTRAINT external_integrations_last_enabled_check CHECK (
        last_enabled_at >= created_at AND last_enabled_at <= updated_at
        ),
    CONSTRAINT external_integrations_tenant_name_key UNIQUE (tenant_id, normalized_name),
    CONSTRAINT external_integrations_id_tenant_key UNIQUE (id, tenant_id)
);

CREATE INDEX external_integrations_tenant_status_idx
    ON external_integrations (tenant_id, status, created_at);

CREATE TABLE api_keys
(
    id              UUID PRIMARY KEY,
    integration_id  UUID                     NOT NULL,
    tenant_id       UUID                     NOT NULL,
    name            VARCHAR(64)              NOT NULL,
    normalized_name VARCHAR(128)             NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT api_keys_integration_tenant_fk
        FOREIGN KEY (integration_id, tenant_id)
            REFERENCES external_integrations (id, tenant_id),
    CONSTRAINT api_keys_name_not_blank_check CHECK (TRIM(name) <> ''),
    CONSTRAINT api_keys_name_stripped_check CHECK (name = TRIM(name)),
    CONSTRAINT api_keys_normalized_name_not_blank_check CHECK (TRIM(normalized_name) <> ''),
    CONSTRAINT api_keys_expiry_check CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT api_keys_revoked_at_check CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT api_keys_integration_name_key UNIQUE (integration_id, normalized_name),
    CONSTRAINT api_keys_id_tenant_key UNIQUE (id, tenant_id),
    CONSTRAINT api_keys_id_integration_key UNIQUE (id, integration_id)
);

CREATE INDEX api_keys_integration_id_idx ON api_keys (integration_id, created_at);

CREATE TABLE api_key_scopes
(
    api_key_id UUID        NOT NULL REFERENCES api_keys (id),
    scope      VARCHAR(32) NOT NULL,
    PRIMARY KEY (api_key_id, scope),
    CONSTRAINT api_key_scopes_scope_check CHECK (scope IN ('ORDERS_READ', 'ORDERS_WRITE'))
);

CREATE TABLE api_key_location_access
(
    api_key_id UUID NOT NULL,
    location_id UUID NOT NULL,
    tenant_id  UUID NOT NULL,
    PRIMARY KEY (api_key_id, location_id),
    CONSTRAINT api_key_location_access_key_tenant_fk
        FOREIGN KEY (api_key_id, tenant_id)
            REFERENCES api_keys (id, tenant_id),
    CONSTRAINT api_key_location_access_location_tenant_fk
        FOREIGN KEY (location_id, tenant_id)
            REFERENCES locations (id, tenant_id)
);

CREATE INDEX api_key_location_access_location_idx
    ON api_key_location_access (location_id, api_key_id);

CREATE TABLE api_key_versions
(
    id          UUID PRIMARY KEY,
    api_key_id  UUID                     NOT NULL REFERENCES api_keys (id),
    secret_hash VARCHAR(64)              NOT NULL UNIQUE,
    issued_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE,
    retired_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT api_key_versions_valid_until_check CHECK (
        valid_until IS NULL OR valid_until > issued_at
        ),
    CONSTRAINT api_key_versions_retired_at_check CHECK (
        retired_at IS NULL OR retired_at >= issued_at
        ),
    CONSTRAINT api_key_versions_id_key_key UNIQUE (id, api_key_id)
);

CREATE INDEX api_key_versions_key_issued_idx
    ON api_key_versions (api_key_id, issued_at DESC);

CREATE TABLE webhook_subscriptions
(
    id              UUID PRIMARY KEY,
    integration_id  UUID                     NOT NULL,
    tenant_id       UUID                     NOT NULL,
    name            VARCHAR(64)              NOT NULL,
    normalized_name VARCHAR(128)             NOT NULL,
    destination_url VARCHAR(2048)            NOT NULL,
    status          VARCHAR(32)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_enabled_at TIMESTAMP WITH TIME ZONE,
    archived_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT webhook_subscriptions_integration_tenant_fk
        FOREIGN KEY (integration_id, tenant_id)
            REFERENCES external_integrations (id, tenant_id),
    CONSTRAINT webhook_subscriptions_name_not_blank_check CHECK (TRIM(name) <> ''),
    CONSTRAINT webhook_subscriptions_name_stripped_check CHECK (name = TRIM(name)),
    CONSTRAINT webhook_subscriptions_normalized_name_not_blank_check CHECK (TRIM(normalized_name) <> ''),
    CONSTRAINT webhook_subscriptions_destination_not_blank_check CHECK (TRIM(destination_url) <> ''),
    CONSTRAINT webhook_subscriptions_status_check CHECK (status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT webhook_subscriptions_archive_check CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
            OR (status <> 'ARCHIVED' AND archived_at IS NULL)
        ),
    CONSTRAINT webhook_subscriptions_last_enabled_check CHECK (
        (status = 'ENABLED' AND last_enabled_at IS NOT NULL)
            OR status <> 'ENABLED'
        ),
    CONSTRAINT webhook_subscriptions_last_enabled_time_check CHECK (
        last_enabled_at IS NULL
            OR (last_enabled_at >= created_at AND last_enabled_at <= updated_at)
        ),
    CONSTRAINT webhook_subscriptions_integration_name_key UNIQUE (integration_id, normalized_name),
    CONSTRAINT webhook_subscriptions_id_tenant_key UNIQUE (id, tenant_id)
);

CREATE INDEX webhook_subscriptions_integration_status_idx
    ON webhook_subscriptions (integration_id, status, created_at);

CREATE TABLE webhook_subscription_location_access
(
    subscription_id UUID NOT NULL,
    location_id     UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    PRIMARY KEY (subscription_id, location_id),
    CONSTRAINT webhook_subscription_location_subscription_tenant_fk
        FOREIGN KEY (subscription_id, tenant_id)
            REFERENCES webhook_subscriptions (id, tenant_id),
    CONSTRAINT webhook_subscription_location_location_tenant_fk
        FOREIGN KEY (location_id, tenant_id)
            REFERENCES locations (id, tenant_id)
);

CREATE INDEX webhook_subscription_location_location_idx
    ON webhook_subscription_location_access (location_id, subscription_id);

CREATE TABLE webhook_subscription_event_types
(
    subscription_id UUID        NOT NULL REFERENCES webhook_subscriptions (id),
    event_type      VARCHAR(32) NOT NULL,
    PRIMARY KEY (subscription_id, event_type),
    CONSTRAINT webhook_subscription_event_types_type_check CHECK (
        event_type IN ('ORDER_CREATED', 'ORDER_READY', 'ORDER_COMPLETED', 'ORDER_CANCELED')
        )
);

CREATE TABLE webhook_signing_secret_versions
(
    id                UUID PRIMARY KEY,
    subscription_id   UUID                     NOT NULL REFERENCES webhook_subscriptions (id),
    encrypted_secret  BYTEA                    NOT NULL,
    encryption_nonce  BYTEA                    NOT NULL,
    issued_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until       TIMESTAMP WITH TIME ZONE,
    retired_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT webhook_signing_secret_nonce_length_check CHECK (OCTET_LENGTH(encryption_nonce) = 12),
    CONSTRAINT webhook_signing_secret_valid_until_check CHECK (
        valid_until IS NULL OR valid_until > issued_at
        ),
    CONSTRAINT webhook_signing_secret_retired_at_check CHECK (
        retired_at IS NULL OR retired_at >= issued_at
        ),
    CONSTRAINT webhook_signing_secret_id_subscription_key UNIQUE (id, subscription_id)
);

CREATE INDEX webhook_signing_secret_subscription_issued_idx
    ON webhook_signing_secret_versions (subscription_id, issued_at DESC);

CREATE TABLE accounts
(
    id            UUID PRIMARY KEY,
    tenant_id     UUID                     NOT NULL REFERENCES tenants (id),
    username      VARCHAR(120)             NOT NULL UNIQUE,
    email         VARCHAR(254) UNIQUE,
    password_hash VARCHAR(255),
    tenant_role   VARCHAR(32)              NOT NULL,
    status        VARCHAR(32)              NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT accounts_username_not_blank_check CHECK (TRIM(username) <> ''),
    CONSTRAINT accounts_username_normalized_check CHECK (username = LOWER(TRIM(username))),
    CONSTRAINT accounts_email_check CHECK (
        email IS NULL OR (TRIM(email) <> '' AND email = LOWER(TRIM(email)))
        ),
    CONSTRAINT accounts_password_hash_not_blank_check CHECK (
        password_hash IS NULL OR TRIM(password_hash) <> ''
        ),
    CONSTRAINT accounts_id_tenant_key UNIQUE (id, tenant_id),
    CONSTRAINT accounts_tenant_role_check CHECK (tenant_role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT accounts_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX accounts_tenant_id_idx ON accounts (tenant_id);

CREATE TABLE external_identities
(
    id         UUID PRIMARY KEY,
    account_id UUID                     NOT NULL REFERENCES accounts (id),
    provider   VARCHAR(120)             NOT NULL,
    subject    VARCHAR(255)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT external_identities_provider_not_blank_check CHECK (TRIM(provider) <> ''),
    CONSTRAINT external_identities_provider_normalized_check CHECK (provider = LOWER(TRIM(provider))),
    CONSTRAINT external_identities_subject_not_blank_check CHECK (TRIM(subject) <> ''),
    CONSTRAINT external_identities_provider_subject_key UNIQUE (provider, subject),
    CONSTRAINT external_identities_account_provider_key UNIQUE (account_id, provider)
);

CREATE INDEX external_identities_account_id_idx ON external_identities (account_id);

CREATE TABLE location_assignments
(
    account_id  UUID                     NOT NULL,
    location_id UUID                     NOT NULL,
    tenant_id   UUID                     NOT NULL,
    role        VARCHAR(32)              NOT NULL,
    status      VARCHAR(32)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (account_id, location_id),
    CONSTRAINT location_assignments_account_key UNIQUE (account_id),
    CONSTRAINT location_assignments_account_tenant_fk
        FOREIGN KEY (account_id, tenant_id)
            REFERENCES accounts (id, tenant_id),
    CONSTRAINT location_assignments_location_tenant_fk
        FOREIGN KEY (location_id, tenant_id)
            REFERENCES locations (id, tenant_id),
    CONSTRAINT location_assignments_role_check CHECK (role IN ('MANAGER', 'OPERATOR')),
    CONSTRAINT location_assignments_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX location_assignments_location_id_idx
    ON location_assignments (location_id, account_id);

CREATE TABLE sessions
(
    id                 UUID PRIMARY KEY,
    account_id         UUID                     NOT NULL REFERENCES accounts (id),
    refresh_token_hash VARCHAR(255)             NOT NULL UNIQUE,
    token_family_id    UUID                     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at       TIMESTAMP WITH TIME ZONE,
    revoked_at         TIMESTAMP WITH TIME ZONE,
    replaced_by_id     UUID REFERENCES sessions (id),
    CONSTRAINT sessions_refresh_token_hash_not_blank_check CHECK (TRIM(refresh_token_hash) <> ''),
    CONSTRAINT sessions_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT sessions_last_used_at_check CHECK (last_used_at IS NULL OR last_used_at >= created_at),
    CONSTRAINT sessions_revoked_at_check CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT sessions_replacement_check CHECK (replaced_by_id IS NULL OR replaced_by_id <> id)
);

CREATE INDEX sessions_account_id_idx ON sessions (account_id);
CREATE INDEX sessions_token_family_id_idx ON sessions (token_family_id);

CREATE TABLE orders
(
    id                           UUID PRIMARY KEY,
    location_id                  UUID                     NOT NULL REFERENCES locations (id),
    tracking_reference           UUID                     NOT NULL UNIQUE,
    label                        VARCHAR(32)              NOT NULL,
    status                       VARCHAR(32)              NOT NULL,
    external_integration_id      UUID REFERENCES external_integrations (id),
    external_idempotency_key     VARCHAR(255),
    external_request_fingerprint VARCHAR(64),
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT orders_label_not_blank_check CHECK (TRIM(label) <> ''),
    CONSTRAINT orders_label_stripped_check CHECK (label = TRIM(label)),
    CONSTRAINT orders_status_check CHECK (
        status IN ('IN_PREPARATION', 'READY', 'COMPLETED', 'CANCELED')
        ),
    CONSTRAINT orders_external_creation_check CHECK (
        (external_integration_id IS NULL
            AND external_idempotency_key IS NULL
            AND external_request_fingerprint IS NULL)
        OR
        (external_integration_id IS NOT NULL
            AND external_idempotency_key IS NOT NULL
            AND external_request_fingerprint IS NOT NULL
            AND OCTET_LENGTH(external_idempotency_key) BETWEEN 1 AND 255)
        ),
    CONSTRAINT orders_external_creation_key UNIQUE (
        external_integration_id,
        location_id,
        external_idempotency_key
        )
);

CREATE INDEX orders_location_created_at_idx ON orders (location_id, created_at DESC);

CREATE TABLE order_history
(
    id                           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id                     UUID                     NOT NULL REFERENCES orders (id),
    status                       VARCHAR(32)              NOT NULL,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    initiator_type               VARCHAR(32),
    initiator_id                 UUID,
    initiator_api_key_id         UUID,
    initiator_api_key_version_id UUID,
    CONSTRAINT order_history_integration_key_fk
        FOREIGN KEY (initiator_api_key_id, initiator_id)
            REFERENCES api_keys (id, integration_id),
    CONSTRAINT order_history_integration_key_version_fk
        FOREIGN KEY (initiator_api_key_version_id, initiator_api_key_id)
            REFERENCES api_key_versions (id, api_key_id),
    CONSTRAINT order_history_initiator_check CHECK (
        (initiator_type IS NULL
            AND initiator_id IS NULL
            AND initiator_api_key_id IS NULL
            AND initiator_api_key_version_id IS NULL)
        OR (initiator_type = 'SYSTEM'
            AND initiator_id IS NULL
            AND initiator_api_key_id IS NULL
            AND initiator_api_key_version_id IS NULL)
        OR (initiator_type = 'USER'
            AND initiator_id IS NOT NULL
            AND initiator_api_key_id IS NULL
            AND initiator_api_key_version_id IS NULL)
        OR (initiator_type = 'INTEGRATION'
            AND initiator_id IS NOT NULL
            AND initiator_api_key_id IS NOT NULL
            AND initiator_api_key_version_id IS NOT NULL)
        )
);

CREATE INDEX order_history_order_id_idx ON order_history (order_id, id);

CREATE TABLE webhook_outbox_events
(
    id                  UUID PRIMARY KEY,
    order_id            UUID                     NOT NULL REFERENCES orders (id),
    tenant_id           UUID                     NOT NULL REFERENCES tenants (id),
    location_id         UUID                     NOT NULL,
    event_type          VARCHAR(32)              NOT NULL,
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    payload             TEXT                     NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    fanout_completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT webhook_outbox_location_tenant_fk
        FOREIGN KEY (location_id, tenant_id)
            REFERENCES locations (id, tenant_id),
    CONSTRAINT webhook_outbox_event_type_check CHECK (
        event_type IN ('ORDER_CREATED', 'ORDER_READY', 'ORDER_COMPLETED', 'ORDER_CANCELED')
        ),
    CONSTRAINT webhook_outbox_payload_not_blank_check CHECK (TRIM(payload) <> ''),
    CONSTRAINT webhook_outbox_fanout_check CHECK (
        fanout_completed_at IS NULL OR fanout_completed_at >= created_at
        )
);

CREATE INDEX webhook_outbox_available_idx
    ON webhook_outbox_events (fanout_completed_at, occurred_at, id);

CREATE TABLE webhook_deliveries
(
    id                 UUID PRIMARY KEY,
    outbox_event_id    UUID                     NOT NULL REFERENCES webhook_outbox_events (id),
    subscription_id    UUID                     NOT NULL REFERENCES webhook_subscriptions (id),
    destination_url    VARCHAR(2048)            NOT NULL,
    payload            TEXT                     NOT NULL,
    status             VARCHAR(32)              NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    claim_token        UUID,
    claimed_at         TIMESTAMP WITH TIME ZONE,
    claim_until        TIMESTAMP WITH TIME ZONE,
    attempted_at       TIMESTAMP WITH TIME ZONE,
    completed_at       TIMESTAMP WITH TIME ZONE,
    response_status    INTEGER,
    response_body      TEXT,
    response_truncated BOOLEAN                  NOT NULL DEFAULT FALSE,
    error_type         VARCHAR(64),
    error_detail       VARCHAR(1024),
    CONSTRAINT webhook_deliveries_event_subscription_key UNIQUE (outbox_event_id, subscription_id),
    CONSTRAINT webhook_deliveries_destination_not_blank_check CHECK (TRIM(destination_url) <> ''),
    CONSTRAINT webhook_deliveries_payload_not_blank_check CHECK (TRIM(payload) <> ''),
    CONSTRAINT webhook_deliveries_status_check CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD_LETTERED')
        ),
    CONSTRAINT webhook_deliveries_claim_check CHECK (
        (status = 'PENDING'
            AND claim_token IS NULL
            AND claimed_at IS NULL
            AND claim_until IS NULL
            AND completed_at IS NULL)
        OR (status = 'PROCESSING'
            AND claim_token IS NOT NULL
            AND claimed_at IS NOT NULL
            AND claim_until IS NOT NULL
            AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'DEAD_LETTERED')
            AND claim_token IS NULL
            AND claimed_at IS NOT NULL
            AND claim_until IS NULL
            AND attempted_at IS NOT NULL
            AND completed_at IS NOT NULL)
        )
);

CREATE INDEX webhook_deliveries_available_idx
    ON webhook_deliveries (status, claim_until, created_at, id);

CREATE TABLE webhook_delivery_signing_versions
(
    delivery_id              UUID NOT NULL REFERENCES webhook_deliveries (id),
    signing_secret_version_id UUID NOT NULL REFERENCES webhook_signing_secret_versions (id),
    PRIMARY KEY (delivery_id, signing_secret_version_id)
);

CREATE INDEX webhook_delivery_signing_version_idx
    ON webhook_delivery_signing_versions (signing_secret_version_id, delivery_id);

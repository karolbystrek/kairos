CREATE TABLE tenants
(
    id UUID PRIMARY KEY
);

CREATE TABLE locations
(
    id              UUID PRIMARY KEY,
    tenant_id       UUID                     NOT NULL REFERENCES tenants (id),
    name            VARCHAR(120)             NOT NULL,
    normalized_name VARCHAR(240)             NOT NULL,
    time_zone       VARCHAR(64)              NOT NULL DEFAULT 'UTC',
    status          VARCHAR(32)              NOT NULL DEFAULT 'ENABLED',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_enabled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT locations_name_not_blank_check CHECK (TRIM(name) <> ''),
    CONSTRAINT locations_name_stripped_check CHECK (name = TRIM(name)),
    CONSTRAINT locations_normalized_name_check CHECK (
        TRIM(normalized_name) <> '' AND normalized_name = TRIM(normalized_name)
        ),
    CONSTRAINT locations_time_zone_not_blank_check CHECK (TRIM(time_zone) <> ''),
    CONSTRAINT locations_time_zone_utc_check CHECK (time_zone = 'UTC'),
    CONSTRAINT locations_status_check CHECK (status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT locations_archive_check CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
            OR (status <> 'ARCHIVED' AND archived_at IS NULL)
        ),
    CONSTRAINT locations_updated_at_check CHECK (updated_at >= created_at),
    CONSTRAINT locations_last_enabled_check CHECK (
        last_enabled_at >= created_at AND last_enabled_at <= updated_at
        ),
    CONSTRAINT locations_id_tenant_key UNIQUE (id, tenant_id)
);

CREATE UNIQUE INDEX locations_tenant_live_name_key ON locations (tenant_id, normalized_name) WHERE status <> 'ARCHIVED';

CREATE INDEX locations_tenant_status_idx ON locations (tenant_id, status, normalized_name, id);

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
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    email VARCHAR(254) NOT NULL UNIQUE,
    provider_subject VARCHAR(128) NOT NULL UNIQUE,
    authentication_cutoff TIMESTAMP WITH TIME ZONE,
    tenant_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT accounts_email_check CHECK (TRIM(email) <> '' AND email = LOWER(TRIM(email))),
    CONSTRAINT accounts_id_tenant_key UNIQUE (id, tenant_id),
    CONSTRAINT accounts_tenant_role_check CHECK (tenant_role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT accounts_status_check CHECK (status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT accounts_archive_check CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL) OR (status <> 'ARCHIVED' AND archived_at IS NULL)),
    CONSTRAINT accounts_updated_at_check CHECK (updated_at >= created_at)
);
CREATE INDEX accounts_tenant_id_idx ON accounts (tenant_id);

CREATE TABLE account_invitations
(
    id                   UUID PRIMARY KEY,
    tenant_id            UUID                     NOT NULL,
    location_id          UUID                     NOT NULL,
    issued_by_account_id UUID                     NOT NULL,
    assignment_role      VARCHAR(32)              NOT NULL,
    token_hash           VARCHAR(64)              NOT NULL UNIQUE,
    state                VARCHAR(32)              NOT NULL,
    revocation_reason    VARCHAR(32),
    revoked_at           TIMESTAMP WITH TIME ZONE,
    redeemed_account_id  UUID,
    redeemed_at          TIMESTAMP WITH TIME ZONE,
    expires_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT account_invitations_location_tenant_fk
        FOREIGN KEY (location_id, tenant_id)
            REFERENCES locations (id, tenant_id),
    CONSTRAINT account_invitations_issuer_tenant_fk
        FOREIGN KEY (issued_by_account_id, tenant_id)
            REFERENCES accounts (id, tenant_id),
    CONSTRAINT account_invitations_redeemed_account_tenant_fk
        FOREIGN KEY (redeemed_account_id, tenant_id)
            REFERENCES accounts (id, tenant_id),
    CONSTRAINT account_invitations_assignment_role_check
        CHECK (assignment_role IN ('MANAGER', 'OPERATOR')),
    CONSTRAINT account_invitations_token_hash_check
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT account_invitations_state_check
        CHECK (state IN ('PENDING', 'REDEEMED', 'REVOKED')),
    CONSTRAINT account_invitations_expiry_check
        CHECK (expires_at = created_at + INTERVAL '7' DAY),
    CONSTRAINT account_invitations_updated_at_check
        CHECK (updated_at >= created_at),
    CONSTRAINT account_invitations_terminal_time_check CHECK (
        (revoked_at IS NULL OR (revoked_at >= created_at AND updated_at = revoked_at))
        AND (redeemed_at IS NULL OR (redeemed_at >= created_at AND updated_at = redeemed_at))
        ),
    CONSTRAINT account_invitations_lifecycle_check CHECK (
        (state = 'PENDING'
            AND revocation_reason IS NULL
            AND revoked_at IS NULL
            AND redeemed_account_id IS NULL
            AND redeemed_at IS NULL)
        OR (state = 'REVOKED'
            AND revocation_reason IS NOT NULL
            AND revoked_at IS NOT NULL
            AND redeemed_account_id IS NULL
            AND redeemed_at IS NULL)
        OR (state = 'REDEEMED'
            AND revocation_reason IS NULL
            AND revoked_at IS NULL
            AND redeemed_account_id IS NOT NULL
            AND redeemed_at IS NOT NULL)
        ),
    CONSTRAINT account_invitations_revocation_reason_check
        CHECK (revocation_reason IS NULL OR revocation_reason IN (
            'STAFF_REVOKED',
            'ISSUER_DISABLED',
            'ISSUER_ARCHIVED',
            'LOCATION_DISABLED',
            'LOCATION_ARCHIVED'
            ))
);

CREATE INDEX account_invitations_tenant_pending_idx
    ON account_invitations (tenant_id, state, expires_at, created_at DESC);
CREATE INDEX account_invitations_location_pending_idx
    ON account_invitations (tenant_id, location_id, assignment_role, state, expires_at, created_at DESC);
CREATE INDEX account_invitations_issuer_pending_idx
    ON account_invitations (issued_by_account_id, state, expires_at);

CREATE TABLE location_assignments
(
    account_id  UUID                     NOT NULL,
    location_id UUID                     NOT NULL,
    tenant_id   UUID                     NOT NULL,
    role        VARCHAR(32)              NOT NULL,
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
    CONSTRAINT location_assignments_role_check CHECK (role IN ('MANAGER', 'OPERATOR'))
);

CREATE INDEX location_assignments_location_id_idx
    ON location_assignments (location_id, account_id);

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY,
    SESSION_ID CHAR(36) NOT NULL UNIQUE,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100)
);
CREATE INDEX SPRING_SESSION_EXPIRY_IDX ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_PRINCIPAL_IDX ON SPRING_SESSION (PRINCIPAL_NAME);
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME)
);

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
        OR (initiator_type IS NOT NULL AND initiator_type = 'SYSTEM'
            AND initiator_id IS NULL
            AND initiator_api_key_id IS NULL
            AND initiator_api_key_version_id IS NULL)
        OR (initiator_type IS NOT NULL AND initiator_type = 'USER'
            AND initiator_id IS NOT NULL
            AND initiator_api_key_id IS NULL
            AND initiator_api_key_version_id IS NULL)
        OR (initiator_type IS NOT NULL AND initiator_type = 'INTEGRATION'
            AND initiator_id IS NOT NULL
            AND initiator_api_key_id IS NOT NULL
            AND initiator_api_key_version_id IS NOT NULL)
        )
);

CREATE INDEX order_history_order_id_idx ON order_history (order_id, id);

CREATE TABLE customer_push_subscriptions
(
    id                       UUID PRIMARY KEY,
    endpoint_hash            VARCHAR(64)              NOT NULL UNIQUE,
    endpoint_origin          VARCHAR(255)             NOT NULL,
    encrypted_endpoint       BYTEA                    NOT NULL,
    endpoint_nonce           BYTEA                    NOT NULL,
    p256dh_key               BYTEA                    NOT NULL,
    encrypted_auth_secret    BYTEA                    NOT NULL,
    auth_secret_nonce        BYTEA                    NOT NULL,
    vapid_key_fingerprint    VARCHAR(64)              NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at               TIMESTAMP WITH TIME ZONE,
    CONSTRAINT customer_push_subscriptions_endpoint_hash_check
        CHECK (endpoint_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT customer_push_subscriptions_endpoint_origin_check
        CHECK (TRIM(endpoint_origin) <> ''),
    CONSTRAINT customer_push_subscriptions_endpoint_nonce_check
        CHECK (OCTET_LENGTH(endpoint_nonce) = 12),
    CONSTRAINT customer_push_subscriptions_p256dh_check
        CHECK (OCTET_LENGTH(p256dh_key) = 65),
    CONSTRAINT customer_push_subscriptions_auth_nonce_check
        CHECK (OCTET_LENGTH(auth_secret_nonce) = 12),
    CONSTRAINT customer_push_subscriptions_vapid_fingerprint_check
        CHECK (TRIM(vapid_key_fingerprint) <> ''),
    CONSTRAINT customer_push_subscriptions_time_check
        CHECK (
            updated_at >= created_at
            AND last_seen_at >= created_at
            AND (expires_at IS NULL OR expires_at > created_at)
        )
);

CREATE INDEX customer_push_subscriptions_dormant_idx
    ON customer_push_subscriptions (expires_at, last_seen_at, id);

CREATE TABLE customer_push_enrollments
(
    id              UUID PRIMARY KEY,
    subscription_id UUID                     NOT NULL
        REFERENCES customer_push_subscriptions (id) ON DELETE CASCADE,
    order_id        UUID                     NOT NULL REFERENCES orders (id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT customer_push_enrollments_subscription_order_key
        UNIQUE (subscription_id, order_id)
);

CREATE INDEX customer_push_enrollments_order_idx
    ON customer_push_enrollments (order_id, subscription_id);

CREATE TABLE order_outbox_events
(
    id                          UUID PRIMARY KEY,
    order_id                    UUID                     NOT NULL REFERENCES orders (id),
    tenant_id                   UUID                     NOT NULL REFERENCES tenants (id),
    location_id                 UUID                     NOT NULL,
    tracking_reference          UUID                     NOT NULL,
    event_type                  VARCHAR(32)              NOT NULL,
    status                      VARCHAR(32)              NOT NULL,
    occurred_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    webhook_payload             TEXT                     NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    webhook_fanout_completed_at TIMESTAMP WITH TIME ZONE,
    push_fanout_completed_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT order_outbox_location_tenant_fk
        FOREIGN KEY (location_id, tenant_id)
            REFERENCES locations (id, tenant_id),
    CONSTRAINT order_outbox_event_type_check CHECK (
        event_type IN ('ORDER_CREATED', 'ORDER_READY', 'ORDER_COMPLETED', 'ORDER_CANCELED')
        ),
    CONSTRAINT order_outbox_status_check CHECK (
        status IN ('IN_PREPARATION', 'READY', 'COMPLETED', 'CANCELED')
        ),
    CONSTRAINT order_outbox_event_status_check CHECK (
        (event_type = 'ORDER_CREATED' AND status = 'IN_PREPARATION')
        OR (event_type = 'ORDER_READY' AND status = 'READY')
        OR (event_type = 'ORDER_COMPLETED' AND status = 'COMPLETED')
        OR (event_type = 'ORDER_CANCELED' AND status = 'CANCELED')
        ),
    CONSTRAINT order_outbox_webhook_payload_not_blank_check
        CHECK (TRIM(webhook_payload) <> ''),
    CONSTRAINT order_outbox_webhook_fanout_check CHECK (
        webhook_fanout_completed_at IS NULL OR webhook_fanout_completed_at >= created_at
        ),
    CONSTRAINT order_outbox_push_fanout_check CHECK (
        push_fanout_completed_at IS NULL OR push_fanout_completed_at >= created_at
        )
);

CREATE INDEX order_outbox_webhook_available_idx
    ON order_outbox_events (webhook_fanout_completed_at, occurred_at, id);

CREATE INDEX order_outbox_push_available_idx
    ON order_outbox_events (push_fanout_completed_at, occurred_at, id);

CREATE TABLE webhook_deliveries
(
    id                 UUID PRIMARY KEY,
    outbox_event_id    UUID                     NOT NULL REFERENCES order_outbox_events (id),
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

CREATE TABLE customer_push_deliveries
(
    id                   UUID PRIMARY KEY,
    outbox_event_id      UUID                     NOT NULL REFERENCES order_outbox_events (id),
    subscription_id      UUID REFERENCES customer_push_subscriptions (id) ON DELETE SET NULL,
    order_id             UUID                     NOT NULL REFERENCES orders (id),
    payload              TEXT,
    status               VARCHAR(32)              NOT NULL,
    attempt_count        INTEGER                  NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    deadline_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    claim_token          UUID,
    claimed_at           TIMESTAMP WITH TIME ZONE,
    claim_until          TIMESTAMP WITH TIME ZONE,
    completed_at         TIMESTAMP WITH TIME ZONE,
    response_status      INTEGER,
    outcome              VARCHAR(64),
    diagnostic           VARCHAR(1024),
    CONSTRAINT customer_push_deliveries_event_subscription_key
        UNIQUE (outbox_event_id, subscription_id),
    CONSTRAINT customer_push_deliveries_status_check CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'ACCEPTED',
            'DEAD_LETTERED',
            'EXPIRED',
            'SUPERSEDED',
            'CANCELED'
            )
        ),
    CONSTRAINT customer_push_deliveries_attempt_count_check
        CHECK (attempt_count >= 0),
    CONSTRAINT customer_push_deliveries_state_check CHECK (
        (status = 'PENDING'
            AND payload IS NOT NULL
            AND claim_token IS NULL
            AND claim_until IS NULL
            AND completed_at IS NULL)
        OR (status = 'PROCESSING'
            AND payload IS NOT NULL
            AND claim_token IS NOT NULL
            AND claimed_at IS NOT NULL
            AND claim_until IS NOT NULL
            AND completed_at IS NULL)
        OR (status IN ('ACCEPTED', 'DEAD_LETTERED', 'EXPIRED', 'SUPERSEDED', 'CANCELED')
            AND payload IS NULL
            AND claim_token IS NULL
            AND claim_until IS NULL
            AND completed_at IS NOT NULL)
        )
);

CREATE INDEX customer_push_deliveries_available_idx
    ON customer_push_deliveries (status, next_attempt_at, claim_until, created_at, id);

CREATE INDEX customer_push_deliveries_cleanup_idx
    ON customer_push_deliveries (status, completed_at, id);

-- Ownership remains normalized; captured references must agree with their parents.
ALTER TABLE orders ADD CONSTRAINT orders_event_identity_key UNIQUE (id, location_id, tracking_reference);
ALTER TABLE order_outbox_events ADD CONSTRAINT order_outbox_order_identity_fk
    FOREIGN KEY (order_id, location_id, tracking_reference) REFERENCES orders(id, location_id, tracking_reference);
ALTER TABLE order_outbox_events ADD CONSTRAINT order_outbox_id_order_key UNIQUE(id, order_id);
ALTER TABLE customer_push_deliveries ADD CONSTRAINT push_delivery_event_order_fk
    FOREIGN KEY(outbox_event_id, order_id) REFERENCES order_outbox_events(id, order_id);

CREATE SCHEMA kairos_security;
REVOKE ALL ON SCHEMA kairos_security FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE FUNCTION kairos_security.setting(key text) RETURNS text
LANGUAGE sql STABLE SET search_path=pg_catalog AS $$
    SELECT COALESCE(current_setting('kairos.' || key, true), '')
$$;
CREATE FUNCTION kairos_security.identity() RETURNS uuid
LANGUAGE plpgsql STABLE SET search_path=pg_catalog AS $$
BEGIN RETURN NULLIF(kairos_security.setting('identity'), '')::uuid;
EXCEPTION WHEN invalid_text_representation THEN RETURN NULL; END
$$;
CREATE FUNCTION kairos_security.includes(key text, id uuid) RETURNS boolean
LANGUAGE sql STABLE SET search_path=pg_catalog AS $$
    SELECT COALESCE(id::text = ANY(string_to_array(kairos_security.setting(key), ',')), false)
$$;
CREATE FUNCTION public.valid_staff(account_id uuid, tenant_id uuid, role text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT 1 FROM public.accounts a WHERE a.id=account_id AND a.tenant_id=valid_staff.tenant_id
        AND a.tenant_role=role AND a.status='ENABLED' AND (
        (a.tenant_role='ADMIN' AND NOT EXISTS(SELECT 1 FROM public.location_assignments x WHERE x.account_id=a.id))
        OR (a.tenant_role='MEMBER' AND EXISTS(SELECT 1 FROM public.location_assignments x JOIN public.locations l ON l.id=x.location_id
            WHERE x.account_id=a.id AND x.tenant_id=a.tenant_id AND l.status='ENABLED'))))
$$;
CREATE FUNCTION public.lock_staff_location() RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF kairos_security.setting('scope')<>'staff' THEN RETURN false; END IF;
    PERFORM 1 FROM public.locations l JOIN public.location_assignments x ON x.location_id=l.id
        WHERE x.account_id=kairos_security.identity() FOR UPDATE OF l;
    RETURN FOUND OR EXISTS(SELECT 1 FROM public.accounts a WHERE a.id=kairos_security.identity() AND a.tenant_role='ADMIN');
END $$;
CREATE FUNCTION public.api_key_authentication(version_id uuid, at_time timestamptz)
RETURNS TABLE(secret_hash varchar,tenant_id uuid,integration_id uuid,api_key_id uuid,api_key_version_id uuid,scopes text[],location_ids uuid[])
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT v.secret_hash,k.tenant_id,k.integration_id,k.id,v.id,
        ARRAY(SELECT s.scope::text FROM public.api_key_scopes s WHERE s.api_key_id=k.id ORDER BY s.scope),
        ARRAY(SELECT g.location_id FROM public.api_key_location_access g JOIN public.locations l ON l.id=g.location_id
            WHERE g.api_key_id=k.id AND l.status='ENABLED' ORDER BY g.location_id)
    FROM public.api_key_versions v JOIN public.api_keys k ON k.id=v.api_key_id
    JOIN public.external_integrations i ON i.id=k.integration_id
    WHERE v.id=version_id AND v.retired_at IS NULL AND (v.valid_until IS NULL OR at_time<v.valid_until)
        AND k.revoked_at IS NULL AND (k.expires_at IS NULL OR at_time<k.expires_at) AND i.status='ENABLED'
$$;
CREATE FUNCTION public.valid_integration(version_id uuid,key_id uuid,integration_id uuid,tenant_id uuid,scopes text[],location_ids uuid[],at_time timestamptz) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT 1 FROM public.api_key_authentication(version_id,at_time) p
        WHERE p.api_key_id=key_id AND p.integration_id=valid_integration.integration_id AND p.tenant_id=valid_integration.tenant_id
        AND p.scopes @> valid_integration.scopes AND valid_integration.scopes @> p.scopes
        AND p.location_ids @> valid_integration.location_ids AND valid_integration.location_ids @> p.location_ids)
$$;
CREATE FUNCTION kairos_security.staff_tenant(tenant uuid, admin_only boolean DEFAULT false) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.setting('scope')='staff' AND EXISTS(SELECT 1 FROM public.accounts a
        WHERE a.id=kairos_security.identity() AND a.tenant_id=tenant AND a.status='ENABLED'
        AND (NOT admin_only OR a.tenant_role='ADMIN'))
$$;
CREATE FUNCTION kairos_security.staff_location(location uuid, manager_only boolean DEFAULT false) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.setting('scope')='staff' AND EXISTS(SELECT 1 FROM public.locations l JOIN public.accounts a ON a.tenant_id=l.tenant_id
        WHERE l.id=location AND a.id=kairos_security.identity() AND a.status='ENABLED' AND (a.tenant_role='ADMIN'
        OR EXISTS(SELECT 1 FROM public.location_assignments x WHERE x.account_id=a.id AND x.location_id=l.id
            AND l.status='ENABLED' AND (NOT manager_only OR x.role='MANAGER'))))
$$;
CREATE FUNCTION kairos_security.account_access(account uuid, tenant uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.staff_tenant(tenant,true)
        OR (kairos_security.staff_tenant(tenant) AND (account=kairos_security.identity()
            OR EXISTS(SELECT 1 FROM public.location_assignments x WHERE x.account_id=account AND x.role='OPERATOR'
                AND kairos_security.staff_location(x.location_id,true))))
$$;
CREATE FUNCTION public.lock_account_location(account_id uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM 1 FROM public.locations l JOIN public.location_assignments x ON x.location_id=l.id
        WHERE x.account_id=lock_account_location.account_id AND kairos_security.account_access(x.account_id,x.tenant_id)
        FOR UPDATE OF l;
    RETURN FOUND;
END $$;
CREATE FUNCTION kairos_security.integration_location(location uuid, writing boolean) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.setting('scope') IN ('integration','integration-command') AND EXISTS(
        SELECT 1 FROM public.api_key_authentication(kairos_security.identity(),NULLIF(kairos_security.setting('at_time'),'')::timestamptz) p
        WHERE location=ANY(p.location_ids) AND
            ((writing AND 'ORDERS_WRITE'=ANY(p.scopes)) OR (NOT writing AND ('ORDERS_READ'=ANY(p.scopes)
                OR (kairos_security.setting('scope')='integration-command' AND 'ORDERS_WRITE'=ANY(p.scopes))))))
$$;
CREATE FUNCTION kairos_security.worker(operation text, id uuid) RETURNS boolean
LANGUAGE sql STABLE SET search_path=pg_catalog AS $$
    SELECT kairos_security.setting('scope')=operation AND kairos_security.identity()=id
$$;
CREATE FUNCTION kairos_security.event_access(event uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.worker('WEBHOOK_FANOUT',event) OR kairos_security.worker('PUSH_FANOUT',event)
        OR EXISTS(SELECT 1 FROM public.webhook_deliveries d WHERE d.outbox_event_id=event AND
            (kairos_security.worker('WEBHOOK_CLAIM',d.id) OR kairos_security.worker('WEBHOOK_COMPLETE',d.id)))
        OR EXISTS(SELECT 1 FROM public.customer_push_deliveries d WHERE d.outbox_event_id=event AND
            (kairos_security.worker('PUSH_CLAIM',d.id) OR kairos_security.worker('PUSH_COMPLETE',d.id)))
$$;
CREATE FUNCTION kairos_security.order_access(order_id uuid, writing boolean DEFAULT false, customer boolean DEFAULT false) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT 1 FROM public.orders o JOIN public.locations l ON l.id=o.location_id WHERE o.id=order_access.order_id
        AND (kairos_security.staff_location(l.id) OR kairos_security.integration_location(l.id,writing)
        OR (NOT writing AND customer AND kairos_security.setting('scope') IN ('tracking','push')
            AND kairos_security.includes('references',o.tracking_reference) AND l.status='ENABLED')
        OR (NOT writing AND EXISTS(SELECT 1 FROM public.order_outbox_events e WHERE e.order_id=o.id AND kairos_security.event_access(e.id)))))
$$;
CREATE FUNCTION kairos_security.subscription_access(subscription uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT (kairos_security.setting('scope')='push' AND kairos_security.includes('subscriptions',subscription))
        OR kairos_security.worker('SUBSCRIPTION_RETIRE',subscription)
        OR EXISTS(SELECT 1 FROM public.customer_push_deliveries d WHERE d.subscription_id=subscription AND
            (kairos_security.worker('PUSH_CLAIM',d.id) OR kairos_security.worker('PUSH_COMPLETE',d.id)))
        OR (kairos_security.setting('scope')='PUSH_FANOUT' AND EXISTS(SELECT 1 FROM public.customer_push_enrollments x
            JOIN public.order_outbox_events e ON e.order_id=x.order_id WHERE x.subscription_id=subscription AND e.id=kairos_security.identity()))
$$;
CREATE FUNCTION kairos_security.webhook_subscription(subscription uuid, secrets boolean DEFAULT false) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT 1 FROM public.webhook_subscriptions s WHERE s.id=subscription AND (
        kairos_security.staff_tenant(s.tenant_id,true)
        OR (kairos_security.setting('scope')='WEBHOOK_FANOUT' AND EXISTS(SELECT 1 FROM public.order_outbox_events e
            JOIN public.webhook_subscription_location_access g ON g.location_id=e.location_id AND g.subscription_id=s.id
            WHERE e.id=kairos_security.identity() AND e.tenant_id=s.tenant_id))
        OR EXISTS(SELECT 1 FROM public.webhook_deliveries d WHERE d.subscription_id=s.id AND
            (kairos_security.worker('WEBHOOK_CLAIM',d.id) OR (NOT secrets AND kairos_security.worker('WEBHOOK_COMPLETE',d.id))))))
$$;
CREATE FUNCTION kairos_security.key_access(key_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT 1 FROM public.api_keys k WHERE k.id=key_id AND (
        kairos_security.staff_tenant(k.tenant_id,true) OR (kairos_security.setting('scope') IN ('integration','integration-command')
        AND EXISTS(SELECT 1 FROM public.api_key_versions v WHERE v.id=kairos_security.identity() AND v.api_key_id=k.id))))
$$;
CREATE FUNCTION kairos_security.push_delivery_access(delivery uuid, subscription uuid, order_id uuid, event uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.worker('PUSH_CLAIM',delivery) OR kairos_security.worker('PUSH_COMPLETE',delivery)
        OR kairos_security.worker('PUSH_CLEANUP',delivery)
        OR (kairos_security.setting('scope')='PUSH_FANOUT' AND EXISTS(SELECT 1 FROM public.order_outbox_events e
            WHERE e.id=kairos_security.identity() AND e.order_id=push_delivery_access.order_id))
$$;

CREATE FUNCTION public.staff_authentication(subject text)
RETURNS TABLE(account_id uuid,tenant_id uuid,tenant_role varchar,authentication_cutoff timestamptz)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT a.id,a.tenant_id,a.tenant_role,a.authentication_cutoff FROM public.accounts a
        WHERE a.provider_subject=subject AND public.valid_staff(a.id,a.tenant_id,a.tenant_role)
$$;
CREATE FUNCTION public.identity_conflict(email text,subject text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT 1 FROM public.accounts a WHERE a.email=identity_conflict.email OR a.provider_subject=subject)
$$;
CREATE FUNCTION public.register_tenant(email text,subject text,at_time timestamptz)
RETURNS TABLE(account_id uuid,tenant_id uuid,tenant_role text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE tenant uuid := gen_random_uuid(); account uuid := gen_random_uuid();
BEGIN
    IF public.identity_conflict(email,subject) THEN RAISE EXCEPTION 'IDENTITY_CONFLICT'; END IF;
    INSERT INTO public.tenants(id) VALUES(tenant);
    INSERT INTO public.accounts(id,tenant_id,email,provider_subject,tenant_role,status,created_at,updated_at)
        VALUES(account,tenant,email,subject,'ADMIN','ENABLED',at_time,at_time);
    RETURN QUERY SELECT account,tenant,'ADMIN'::text;
END $$;
CREATE FUNCTION kairos_security.issuer_eligible(invitation uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT 1 FROM public.account_invitations i JOIN public.accounts a ON a.id=i.issued_by_account_id
        JOIN public.locations l ON l.id=i.location_id WHERE i.id=invitation AND a.status='ENABLED' AND l.status='ENABLED'
        AND (a.tenant_role='ADMIN' OR (i.assignment_role='OPERATOR' AND EXISTS(SELECT 1 FROM public.location_assignments x
            WHERE x.account_id=a.id AND x.location_id=i.location_id AND x.role='MANAGER'))))
$$;
CREATE FUNCTION public.invitation_preview(hash text,at_time timestamptz)
RETURNS TABLE(location_name varchar,assignment_role varchar,expires_at timestamptz,state varchar,issuer_eligible boolean,location_enabled boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT l.name,i.assignment_role,i.expires_at,i.state,kairos_security.issuer_eligible(i.id),l.status='ENABLED'
        FROM public.account_invitations i JOIN public.locations l ON l.id=i.location_id WHERE i.token_hash=hash
$$;
CREATE FUNCTION public.redeem_invitation(hash text,email text,subject text,at_time timestamptz)
RETURNS TABLE(account_id uuid,tenant_id uuid,tenant_role text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE invitation public.account_invitations; account uuid := gen_random_uuid();
BEGIN
    SELECT * INTO invitation FROM public.account_invitations i WHERE i.token_hash=hash;
    IF NOT FOUND THEN RAISE EXCEPTION 'INVITATION_NOT_FOUND'; END IF;
    -- Location-before-invitation avoids cascade/FK lock inversion; issuer revocation uses the invitation lock.
    PERFORM 1 FROM public.locations l WHERE l.id=invitation.location_id FOR SHARE;
    SELECT * INTO invitation FROM public.account_invitations i WHERE i.token_hash=hash FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'INVITATION_NOT_FOUND'; END IF;
    IF invitation.state='REDEEMED' THEN RAISE EXCEPTION 'INVITATION_REDEEMED'; END IF;
    IF invitation.state='REVOKED' THEN RAISE EXCEPTION 'INVITATION_REVOKED'; END IF;
    IF at_time>=invitation.expires_at THEN RAISE EXCEPTION 'INVITATION_EXPIRED'; END IF;
    IF NOT kairos_security.issuer_eligible(invitation.id) THEN RAISE EXCEPTION 'INVITATION_REVOKED'; END IF;
    IF public.identity_conflict(email,subject) THEN RAISE EXCEPTION 'IDENTITY_CONFLICT'; END IF;
    INSERT INTO public.accounts(id,tenant_id,email,provider_subject,tenant_role,status,created_at,updated_at)
        VALUES(account,invitation.tenant_id,email,subject,'MEMBER','ENABLED',at_time,at_time);
    INSERT INTO public.location_assignments(account_id,location_id,tenant_id,role,created_at,updated_at)
        VALUES(account,invitation.location_id,invitation.tenant_id,invitation.assignment_role,at_time,at_time);
    UPDATE public.account_invitations SET state='REDEEMED',redeemed_account_id=account,redeemed_at=at_time,updated_at=at_time WHERE id=invitation.id;
    RETURN QUERY SELECT account,invitation.tenant_id,'MEMBER'::text;
END $$;
CREATE FUNCTION public.verify_push_subscription(hash text)
RETURNS TABLE(id uuid,p256dh_key bytea,encrypted_auth_secret bytea,auth_secret_nonce bytea)
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT s.id,s.p256dh_key,s.encrypted_auth_secret,s.auth_secret_nonce FROM public.customer_push_subscriptions s
        WHERE s.endpoint_hash=hash FOR UPDATE
$$;
CREATE FUNCTION public.create_push_subscription(id uuid,hash text,origin text,endpoint bytea,endpoint_nonce bytea,
    p256dh bytea,auth bytea,auth_nonce bytea,fingerprint text,expiry timestamptz,at_time timestamptz) RETURNS uuid
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    INSERT INTO public.customer_push_subscriptions(id,endpoint_hash,endpoint_origin,encrypted_endpoint,endpoint_nonce,p256dh_key,
        encrypted_auth_secret,auth_secret_nonce,vapid_key_fingerprint,expires_at,created_at,updated_at,last_seen_at)
    VALUES(id,hash,origin,endpoint,endpoint_nonce,p256dh,auth,auth_nonce,fingerprint,expiry,at_time,at_time,at_time)
    ON CONFLICT(endpoint_hash) DO NOTHING RETURNING id
$$;
CREATE FUNCTION public.count_push_enrollments(reference uuid) RETURNS bigint
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT count(*) FROM public.customer_push_enrollments x JOIN public.orders o ON o.id=x.order_id
        WHERE o.tracking_reference=reference AND kairos_security.setting('scope')='push'
        AND kairos_security.includes('references',reference)
$$;

CREATE FUNCTION kairos_security.integration_access(integration uuid,tenant uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.staff_tenant(tenant,true)
        OR (kairos_security.setting('scope') IN ('integration','integration-command') AND EXISTS(SELECT 1 FROM public.api_key_versions v
            JOIN public.api_keys k ON k.id=v.api_key_id WHERE v.id=kairos_security.identity() AND k.integration_id=integration))
        OR EXISTS(SELECT 1 FROM public.webhook_subscriptions s WHERE s.integration_id=integration
            AND kairos_security.webhook_subscription(s.id))
$$;

CREATE FUNCTION public.cancel_push_deliveries(subscription uuid,orders uuid[],at_time timestamptz) RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE affected integer;
BEGIN
    IF kairos_security.setting('scope') NOT IN ('push','SUBSCRIPTION_RETIRE','PUSH_CLAIM','PUSH_COMPLETE')
        OR NOT kairos_security.subscription_access(subscription) THEN
        RAISE EXCEPTION 'Subscription cancellation capability required' USING ERRCODE='42501';
    END IF;
    UPDATE public.customer_push_deliveries d SET status='CANCELED',payload=NULL,completed_at=at_time,outcome='SUBSCRIPTION_REMOVED'
        WHERE d.subscription_id=subscription AND d.status='PENDING' AND (orders IS NULL OR d.order_id=ANY(orders));
    GET DIAGNOSTICS affected=ROW_COUNT;
    RETURN affected;
END $$;

-- Command-specific row policies. Absence of context has no permissive policy.
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON tenants FOR SELECT USING (kairos_security.staff_tenant(id));
CREATE POLICY scoped_update ON tenants FOR UPDATE USING (kairos_security.staff_tenant(id,true)) WITH CHECK (false);
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON locations FOR SELECT USING (kairos_security.staff_location(id) OR kairos_security.integration_location(id,false) OR EXISTS(SELECT 1 FROM orders o WHERE o.location_id=locations.id AND kairos_security.order_access(o.id,false,true)));
CREATE POLICY scoped_insert ON locations FOR INSERT WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
CREATE POLICY scoped_update ON locations FOR UPDATE USING (kairos_security.staff_location(id) OR kairos_security.integration_location(id,false) OR EXISTS(SELECT 1 FROM orders o WHERE o.location_id=locations.id AND kairos_security.order_access(o.id,false,true))) WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON accounts FOR SELECT USING (kairos_security.account_access(id,tenant_id));
CREATE POLICY scoped_update ON accounts FOR UPDATE USING (kairos_security.account_access(id,tenant_id)) WITH CHECK (kairos_security.account_access(id,tenant_id));
ALTER TABLE location_assignments ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON location_assignments FOR SELECT USING (kairos_security.account_access(account_id,tenant_id));
CREATE POLICY scoped_update ON location_assignments FOR UPDATE USING (kairos_security.account_access(account_id,tenant_id)) WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
ALTER TABLE account_invitations ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON account_invitations FOR SELECT USING (kairos_security.staff_tenant(tenant_id,true) OR (assignment_role='OPERATOR' AND kairos_security.staff_location(location_id,true)));
CREATE POLICY scoped_insert ON account_invitations FOR INSERT WITH CHECK (kairos_security.staff_tenant(tenant_id,true) OR (assignment_role='OPERATOR' AND kairos_security.staff_location(location_id,true)));
CREATE POLICY scoped_update ON account_invitations FOR UPDATE USING (kairos_security.staff_tenant(tenant_id,true) OR (assignment_role='OPERATOR' AND kairos_security.staff_location(location_id,true))) WITH CHECK (kairos_security.staff_tenant(tenant_id,true) OR (assignment_role='OPERATOR' AND kairos_security.staff_location(location_id,true)));
ALTER TABLE external_integrations ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON external_integrations FOR SELECT USING (kairos_security.integration_access(id,tenant_id));
CREATE POLICY scoped_insert ON external_integrations FOR INSERT WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
CREATE POLICY scoped_update ON external_integrations FOR UPDATE USING (kairos_security.integration_access(id,tenant_id)) WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON api_keys FOR SELECT USING (kairos_security.key_access(id));
CREATE POLICY scoped_insert ON api_keys FOR INSERT WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
CREATE POLICY scoped_update ON api_keys FOR UPDATE USING (kairos_security.staff_tenant(tenant_id,true)) WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
ALTER TABLE api_key_scopes ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON api_key_scopes FOR SELECT USING (kairos_security.key_access(api_key_id));
CREATE POLICY scoped_insert ON api_key_scopes FOR INSERT WITH CHECK (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
CREATE POLICY scoped_update ON api_key_scopes FOR UPDATE USING (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true))) WITH CHECK (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
CREATE POLICY scoped_delete ON api_key_scopes FOR DELETE USING (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
ALTER TABLE api_key_versions ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON api_key_versions FOR SELECT USING (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)) OR (kairos_security.setting('scope') IN ('integration','integration-command') AND id=kairos_security.identity()));
CREATE POLICY scoped_insert ON api_key_versions FOR INSERT WITH CHECK (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
CREATE POLICY scoped_update ON api_key_versions FOR UPDATE USING (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true))) WITH CHECK (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
CREATE POLICY scoped_delete ON api_key_versions FOR DELETE USING (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
ALTER TABLE api_key_location_access ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON api_key_location_access FOR SELECT USING (kairos_security.key_access(api_key_id));
CREATE POLICY scoped_insert ON api_key_location_access FOR INSERT WITH CHECK (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
CREATE POLICY scoped_update ON api_key_location_access FOR UPDATE USING (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true))) WITH CHECK (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
CREATE POLICY scoped_delete ON api_key_location_access FOR DELETE USING (EXISTS(SELECT 1 FROM api_keys k WHERE k.id=api_key_id AND kairos_security.staff_tenant(k.tenant_id,true)));
ALTER TABLE webhook_subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON webhook_subscriptions FOR SELECT USING (kairos_security.webhook_subscription(id));
CREATE POLICY scoped_insert ON webhook_subscriptions FOR INSERT WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
CREATE POLICY scoped_update ON webhook_subscriptions FOR UPDATE USING (kairos_security.webhook_subscription(id)) WITH CHECK (kairos_security.staff_tenant(tenant_id,true));
ALTER TABLE webhook_subscription_location_access ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON webhook_subscription_location_access FOR SELECT USING (kairos_security.webhook_subscription(subscription_id));
CREATE POLICY scoped_insert ON webhook_subscription_location_access FOR INSERT WITH CHECK (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
CREATE POLICY scoped_update ON webhook_subscription_location_access FOR UPDATE USING (kairos_security.webhook_subscription(subscription_id)) WITH CHECK (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
CREATE POLICY scoped_delete ON webhook_subscription_location_access FOR DELETE USING (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
ALTER TABLE webhook_subscription_event_types ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON webhook_subscription_event_types FOR SELECT USING (kairos_security.webhook_subscription(subscription_id));
CREATE POLICY scoped_insert ON webhook_subscription_event_types FOR INSERT WITH CHECK (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
CREATE POLICY scoped_update ON webhook_subscription_event_types FOR UPDATE USING (kairos_security.webhook_subscription(subscription_id)) WITH CHECK (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
CREATE POLICY scoped_delete ON webhook_subscription_event_types FOR DELETE USING (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
ALTER TABLE webhook_signing_secret_versions ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON webhook_signing_secret_versions FOR SELECT USING (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)) OR (kairos_security.setting('scope')='WEBHOOK_FANOUT' AND kairos_security.webhook_subscription(subscription_id,true)) OR EXISTS(SELECT 1 FROM webhook_delivery_signing_versions v WHERE v.signing_secret_version_id=webhook_signing_secret_versions.id AND kairos_security.worker('WEBHOOK_CLAIM',v.delivery_id)));
CREATE POLICY scoped_insert ON webhook_signing_secret_versions FOR INSERT WITH CHECK (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
CREATE POLICY scoped_update ON webhook_signing_secret_versions FOR UPDATE USING (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true))) WITH CHECK (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
CREATE POLICY scoped_delete ON webhook_signing_secret_versions FOR DELETE USING (EXISTS(SELECT 1 FROM webhook_subscriptions s WHERE s.id=subscription_id AND kairos_security.staff_tenant(s.tenant_id,true)));
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON orders FOR SELECT USING (kairos_security.order_access(id,false,true));
CREATE POLICY scoped_insert ON orders FOR INSERT WITH CHECK (kairos_security.staff_location(location_id) OR kairos_security.integration_location(location_id,true));
CREATE POLICY scoped_update ON orders FOR UPDATE USING (kairos_security.order_access(id,false,true)) WITH CHECK (kairos_security.staff_location(location_id) OR kairos_security.integration_location(location_id,true));
ALTER TABLE order_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON order_history FOR SELECT USING (kairos_security.order_access(order_id,false,false));
CREATE POLICY scoped_insert ON order_history FOR INSERT WITH CHECK (kairos_security.order_access(order_id,true,false));
ALTER TABLE order_outbox_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON order_outbox_events FOR SELECT USING (kairos_security.staff_location(location_id) OR kairos_security.integration_location(location_id,true) OR kairos_security.event_access(id));
CREATE POLICY scoped_insert ON order_outbox_events FOR INSERT WITH CHECK (kairos_security.staff_location(location_id) OR kairos_security.integration_location(location_id,true));
CREATE POLICY scoped_update ON order_outbox_events FOR UPDATE USING (kairos_security.worker('WEBHOOK_FANOUT',id) OR kairos_security.worker('PUSH_FANOUT',id)) WITH CHECK (kairos_security.worker('WEBHOOK_FANOUT',id) OR kairos_security.worker('PUSH_FANOUT',id));
ALTER TABLE webhook_deliveries ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON webhook_deliveries FOR SELECT USING (kairos_security.worker('WEBHOOK_CLAIM',id) OR kairos_security.worker('WEBHOOK_COMPLETE',id) OR kairos_security.worker('WEBHOOK_FANOUT',outbox_event_id));
CREATE POLICY scoped_insert ON webhook_deliveries FOR INSERT WITH CHECK (kairos_security.worker('WEBHOOK_FANOUT',outbox_event_id) AND kairos_security.webhook_subscription(subscription_id));
CREATE POLICY scoped_update ON webhook_deliveries FOR UPDATE USING (kairos_security.worker('WEBHOOK_CLAIM',id) OR kairos_security.worker('WEBHOOK_COMPLETE',id)) WITH CHECK (kairos_security.worker('WEBHOOK_CLAIM',id) OR kairos_security.worker('WEBHOOK_COMPLETE',id));
ALTER TABLE webhook_delivery_signing_versions ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON webhook_delivery_signing_versions FOR SELECT USING (EXISTS(SELECT 1 FROM webhook_deliveries d WHERE d.id=delivery_id AND kairos_security.worker('WEBHOOK_FANOUT',d.outbox_event_id)) OR kairos_security.worker('WEBHOOK_CLAIM',delivery_id));
CREATE POLICY scoped_insert ON webhook_delivery_signing_versions FOR INSERT WITH CHECK (EXISTS(SELECT 1 FROM webhook_deliveries d WHERE d.id=delivery_id AND kairos_security.worker('WEBHOOK_FANOUT',d.outbox_event_id)));
ALTER TABLE customer_push_subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON customer_push_subscriptions FOR SELECT USING (kairos_security.subscription_access(id));
CREATE POLICY scoped_update ON customer_push_subscriptions FOR UPDATE USING (kairos_security.setting('scope') IN ('push','SUBSCRIPTION_RETIRE','PUSH_CLAIM','PUSH_COMPLETE') AND kairos_security.subscription_access(id)) WITH CHECK (kairos_security.setting('scope') IN ('push','SUBSCRIPTION_RETIRE','PUSH_CLAIM','PUSH_COMPLETE') AND kairos_security.subscription_access(id));
CREATE POLICY scoped_delete ON customer_push_subscriptions FOR DELETE USING (kairos_security.setting('scope') IN ('push','SUBSCRIPTION_RETIRE','PUSH_CLAIM','PUSH_COMPLETE') AND kairos_security.subscription_access(id));
ALTER TABLE customer_push_enrollments ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON customer_push_enrollments FOR SELECT USING ((kairos_security.setting('scope') IN ('push','SUBSCRIPTION_RETIRE','PUSH_CLAIM','PUSH_COMPLETE') AND kairos_security.subscription_access(subscription_id)) OR (kairos_security.setting('scope')='PUSH_FANOUT' AND EXISTS(SELECT 1 FROM order_outbox_events e WHERE e.id=kairos_security.identity() AND e.order_id=customer_push_enrollments.order_id)));
CREATE POLICY scoped_insert ON customer_push_enrollments FOR INSERT WITH CHECK (kairos_security.setting('scope')='push' AND kairos_security.subscription_access(subscription_id) AND kairos_security.order_access(order_id,false,true));
CREATE POLICY scoped_delete ON customer_push_enrollments FOR DELETE USING ((kairos_security.setting('scope') IN ('push','SUBSCRIPTION_RETIRE','PUSH_CLAIM','PUSH_COMPLETE') AND kairos_security.subscription_access(subscription_id)) OR (kairos_security.setting('scope')='PUSH_FANOUT' AND EXISTS(SELECT 1 FROM order_outbox_events e WHERE e.id=kairos_security.identity() AND e.order_id=customer_push_enrollments.order_id)));
ALTER TABLE customer_push_deliveries ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_select ON customer_push_deliveries FOR SELECT USING (kairos_security.push_delivery_access(id,subscription_id,order_id,outbox_event_id));
CREATE POLICY scoped_insert ON customer_push_deliveries FOR INSERT WITH CHECK (kairos_security.worker('PUSH_FANOUT',outbox_event_id) AND kairos_security.subscription_access(subscription_id));
CREATE POLICY scoped_update ON customer_push_deliveries FOR UPDATE USING (kairos_security.push_delivery_access(id,subscription_id,order_id,outbox_event_id)) WITH CHECK (kairos_security.push_delivery_access(id,subscription_id,order_id,outbox_event_id));
CREATE POLICY scoped_delete ON customer_push_deliveries FOR DELETE USING (kairos_security.worker('PUSH_CLEANUP',id));

-- Discovery locks one identity; the same transaction must bind and process it.
CREATE FUNCTION public.next_webhook_fanout() RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.order_outbox_events WHERE webhook_fanout_completed_at IS NULL ORDER BY occurred_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','WEBHOOK_FANOUT:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE FUNCTION public.next_push_fanout() RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.order_outbox_events WHERE push_fanout_completed_at IS NULL ORDER BY occurred_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','PUSH_FANOUT:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE FUNCTION public.next_webhook_delivery(at_time timestamptz) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.webhook_deliveries WHERE status='PENDING' OR (status='PROCESSING' AND claim_until<=at_time) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','WEBHOOK_CLAIM:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE FUNCTION public.next_push_delivery(at_time timestamptz) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.customer_push_deliveries WHERE (status='PENDING' AND next_attempt_at<=at_time) OR (status='PROCESSING' AND claim_until<=at_time) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','PUSH_CLAIM:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE FUNCTION public.next_expired_push_subscription(at_time timestamptz) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.customer_push_subscriptions WHERE expires_at IS NOT NULL AND expires_at<=at_time ORDER BY expires_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','SUBSCRIPTION_RETIRE:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE FUNCTION public.next_dormant_push_subscription(cutoff timestamptz) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.customer_push_subscriptions WHERE last_seen_at<cutoff AND NOT EXISTS(SELECT 1 FROM public.customer_push_enrollments x WHERE x.subscription_id=customer_push_subscriptions.id) ORDER BY last_seen_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','SUBSCRIPTION_RETIRE:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE FUNCTION public.next_terminal_push_delivery(successful_cutoff timestamptz,failed_cutoff timestamptz) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.customer_push_deliveries WHERE (status='ACCEPTED' AND completed_at<successful_cutoff) OR (status IN ('DEAD_LETTERED','EXPIRED','SUPERSEDED','CANCELED') AND completed_at<failed_cutoff) ORDER BY completed_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','PUSH_CLEANUP:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE FUNCTION public.valid_worker(operation text,resource uuid,token uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF operation IN ('WEBHOOK_FANOUT','PUSH_FANOUT','WEBHOOK_CLAIM','PUSH_CLAIM','SUBSCRIPTION_RETIRE','PUSH_CLEANUP') THEN
        RETURN kairos_security.setting('selected')=operation || ':' || resource::text;
    ELSIF operation='WEBHOOK_COMPLETE' THEN
        PERFORM 1 FROM public.webhook_deliveries d WHERE d.id=resource AND d.status='PROCESSING' AND d.claim_token=token FOR UPDATE;
        RETURN FOUND;
    ELSIF operation='PUSH_COMPLETE' THEN
        PERFORM 1 FROM public.customer_push_deliveries d WHERE d.id=resource AND d.status='PROCESSING' AND d.claim_token=token FOR UPDATE;
        RETURN FOUND;
    END IF;
    RETURN false;
END $$;

CREATE FUNCTION kairos_security.stable_ownership() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE column_name text;
BEGIN
    FOREACH column_name IN ARRAY TG_ARGV LOOP
        IF (to_jsonb(OLD)->column_name) IS DISTINCT FROM (to_jsonb(NEW)->column_name) THEN
            RAISE EXCEPTION 'Ownership and captured identity are immutable' USING ERRCODE='23514';
        END IF;
    END LOOP;
    RETURN NEW;
END $$;
CREATE TRIGGER stable_ownership BEFORE UPDATE ON locations FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','tenant_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','tenant_id','provider_subject','email','tenant_role');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON location_assignments FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('account_id','location_id','tenant_id','role');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON account_invitations FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','tenant_id','location_id','issued_by_account_id','assignment_role','token_hash');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON external_integrations FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','tenant_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON api_keys FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','tenant_id','integration_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON api_key_versions FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','api_key_id','secret_hash');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON api_key_location_access FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('api_key_id','location_id','tenant_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON webhook_subscriptions FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','tenant_id','integration_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON webhook_subscription_location_access FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('subscription_id','location_id','tenant_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON webhook_signing_secret_versions FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','subscription_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','location_id','tracking_reference','label','external_integration_id','external_idempotency_key','external_request_fingerprint');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON order_outbox_events FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','order_id','tenant_id','location_id','tracking_reference','event_type','status','occurred_at','webhook_payload','created_at');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON webhook_deliveries FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','outbox_event_id','subscription_id','destination_url','payload');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON customer_push_subscriptions FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','endpoint_hash','p256dh_key');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON customer_push_enrollments FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','subscription_id','order_id');
CREATE TRIGGER stable_ownership BEFORE UPDATE ON customer_push_deliveries FOR EACH ROW EXECUTE FUNCTION kairos_security.stable_ownership('id','outbox_event_id','order_id');

CREATE FUNCTION kairos_security.consistent_relationships() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF TG_TABLE_NAME='orders' THEN
        IF NEW.external_integration_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.locations l JOIN public.external_integrations i ON i.tenant_id=l.tenant_id
            WHERE l.id=NEW.location_id AND i.id=NEW.external_integration_id) THEN
            RAISE EXCEPTION 'Order integration tenant mismatch' USING ERRCODE='23503';
        END IF;
    ELSIF TG_TABLE_NAME='webhook_deliveries' THEN
        IF NOT EXISTS(SELECT 1 FROM public.order_outbox_events e JOIN public.webhook_subscriptions s ON s.tenant_id=e.tenant_id
            WHERE e.id=NEW.outbox_event_id AND s.id=NEW.subscription_id) THEN
            RAISE EXCEPTION 'Webhook recipient tenant mismatch' USING ERRCODE='23503';
        END IF;
    ELSIF TG_TABLE_NAME='webhook_delivery_signing_versions' THEN
        IF NOT EXISTS(SELECT 1 FROM public.webhook_deliveries d JOIN public.webhook_signing_secret_versions s ON s.subscription_id=d.subscription_id
            WHERE d.id=NEW.delivery_id AND s.id=NEW.signing_secret_version_id) THEN
            RAISE EXCEPTION 'Captured signing subscription mismatch' USING ERRCODE='23503';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER consistent_relationships BEFORE INSERT OR UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION kairos_security.consistent_relationships();
CREATE TRIGGER consistent_relationships BEFORE INSERT OR UPDATE ON webhook_deliveries FOR EACH ROW EXECUTE FUNCTION kairos_security.consistent_relationships();
CREATE TRIGGER consistent_relationships BEFORE INSERT OR UPDATE ON webhook_delivery_signing_versions FOR EACH ROW EXECUTE FUNCTION kairos_security.consistent_relationships();

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA kairos_security FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public,kairos_security TO "${runtimeUser}";
GRANT SELECT,INSERT,UPDATE,DELETE ON tenants,locations,accounts,location_assignments,account_invitations,
    external_integrations,api_keys,api_key_scopes,api_key_versions,api_key_location_access,
    webhook_subscriptions,webhook_subscription_location_access,webhook_subscription_event_types,webhook_signing_secret_versions,
    orders,order_history,order_outbox_events,webhook_deliveries,webhook_delivery_signing_versions,
    customer_push_subscriptions,customer_push_enrollments,customer_push_deliveries,
    spring_session,spring_session_attributes TO "${runtimeUser}";
GRANT USAGE ON SEQUENCE order_history_id_seq TO "${runtimeUser}";
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA kairos_security TO "${runtimeUser}";
GRANT EXECUTE ON FUNCTION public.valid_staff(uuid,uuid,text),public.lock_staff_location(),public.lock_account_location(uuid),
    public.api_key_authentication(uuid,timestamptz),public.valid_integration(uuid,uuid,uuid,uuid,text[],uuid[],timestamptz),
    public.staff_authentication(text),public.identity_conflict(text,text),public.register_tenant(text,text,timestamptz),
    public.invitation_preview(text,timestamptz),public.redeem_invitation(text,text,text,timestamptz),
    public.verify_push_subscription(text),public.create_push_subscription(uuid,text,text,bytea,bytea,bytea,bytea,bytea,text,timestamptz,timestamptz),
    public.count_push_enrollments(uuid),public.cancel_push_deliveries(uuid,uuid[],timestamptz),public.next_webhook_fanout(),public.next_push_fanout(),
    public.next_webhook_delivery(timestamptz),public.next_push_delivery(timestamptz),
    public.next_expired_push_subscription(timestamptz),public.next_dormant_push_subscription(timestamptz),
    public.next_terminal_push_delivery(timestamptz,timestamptz),public.valid_worker(text,uuid,uuid) TO "${runtimeUser}";
COMMENT ON SCHEMA kairos_security IS 'kairos-rls-v1';

CREATE TABLE tenants
(
    id UUID PRIMARY KEY
);

CREATE TABLE locations
(
    id        UUID PRIMARY KEY,
    tenant_id UUID         NOT NULL REFERENCES tenants (id),
    CONSTRAINT locations_id_tenant_key UNIQUE (id, tenant_id)
);

CREATE INDEX locations_tenant_id_idx ON locations (tenant_id);

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
    id                 UUID PRIMARY KEY,
    location_id        UUID                     NOT NULL REFERENCES locations (id),
    tracking_reference UUID                     NOT NULL UNIQUE,
    status             VARCHAR(32)              NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX orders_location_created_at_idx ON orders (location_id, created_at DESC);

CREATE TABLE order_history
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       UUID                     NOT NULL REFERENCES orders (id),
    status         VARCHAR(32)              NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    initiator_type VARCHAR(32),
    initiator_id   UUID,
    CONSTRAINT order_history_initiator_check CHECK (
        (initiator_type IS NULL AND initiator_id IS NULL)
            OR (initiator_type = 'SYSTEM' AND initiator_id IS NULL)
            OR (initiator_type IN ('USER', 'INTEGRATION') AND initiator_id IS NOT NULL)
        )
);

CREATE INDEX order_history_order_id_idx ON order_history (order_id, id);

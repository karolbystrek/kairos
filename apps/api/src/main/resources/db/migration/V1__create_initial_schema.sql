CREATE TABLE tenants
(
    id   UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE locations
(
    id        UUID PRIMARY KEY,
    tenant_id UUID         NOT NULL REFERENCES tenants (id),
    name      VARCHAR(120) NOT NULL
);

CREATE INDEX locations_tenant_id_idx ON locations (tenant_id);

CREATE TABLE orders
(
    id                 UUID PRIMARY KEY,
    location_id        UUID                     NOT NULL REFERENCES locations (id),
    tracking_reference UUID                     NOT NULL UNIQUE,
    label              VARCHAR(80)              NOT NULL,
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

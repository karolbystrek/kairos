-- Orders are never deleted, so queue reads must not walk a location's whole history.
CREATE INDEX orders_active_location_created_at_idx ON orders (location_id, created_at DESC)
    WHERE status IN ('IN_PREPARATION', 'READY');

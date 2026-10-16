-- Orders nobody closed lapse into EXPIRED; only the system sets it, through the ORDER_EXPIRY worker scope.
ALTER TABLE orders DROP CONSTRAINT orders_status_check, ADD CONSTRAINT orders_status_check
    CHECK (status IN ('IN_PREPARATION','READY','COMPLETED','CANCELED','EXPIRED'));
ALTER TABLE order_outbox_events
    DROP CONSTRAINT order_outbox_event_type_check, ADD CONSTRAINT order_outbox_event_type_check
        CHECK (event_type IN ('ORDER_CREATED','ORDER_READY','ORDER_COMPLETED','ORDER_CANCELED','ORDER_EXPIRED')),
    DROP CONSTRAINT order_outbox_status_check, ADD CONSTRAINT order_outbox_status_check
        CHECK (status IN ('IN_PREPARATION','READY','COMPLETED','CANCELED','EXPIRED')),
    DROP CONSTRAINT order_outbox_event_status_check, ADD CONSTRAINT order_outbox_event_status_check CHECK (
        (event_type='ORDER_CREATED' AND status='IN_PREPARATION') OR (event_type='ORDER_READY' AND status='READY')
        OR (event_type='ORDER_COMPLETED' AND status='COMPLETED') OR (event_type='ORDER_CANCELED' AND status='CANCELED')
        OR (event_type='ORDER_EXPIRED' AND status='EXPIRED'));
ALTER TABLE webhook_subscription_event_types DROP CONSTRAINT webhook_subscription_event_types_type_check,
    ADD CONSTRAINT webhook_subscription_event_types_type_check
        CHECK (event_type IN ('ORDER_CREATED','ORDER_READY','ORDER_COMPLETED','ORDER_CANCELED','ORDER_EXPIRED'));

CREATE INDEX orders_active_updated_at_idx ON orders (updated_at, id) WHERE status IN ('IN_PREPARATION','READY');

CREATE OR REPLACE FUNCTION kairos_security.order_access(order_id uuid, writing boolean DEFAULT false, customer boolean DEFAULT false) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT kairos_security.worker('ORDER_EXPIRY',order_access.order_id) OR EXISTS(SELECT 1 FROM public.orders o
        JOIN public.locations l ON l.id=o.location_id WHERE o.id=order_access.order_id
        AND (kairos_security.staff_location(l.id) OR kairos_security.integration_location(l.id,writing)
        OR (NOT writing AND customer AND kairos_security.setting('scope') IN ('tracking','push')
            AND kairos_security.includes('references',o.tracking_reference)
            AND (l.status='ENABLED' OR o.status IN ('COMPLETED','CANCELED','EXPIRED')))
        OR (NOT writing AND EXISTS(SELECT 1 FROM public.order_outbox_events e WHERE e.order_id=o.id AND kairos_security.event_access(e.id)))))
$$;

DROP POLICY scoped_update ON orders;
CREATE POLICY scoped_update ON orders FOR UPDATE USING (kairos_security.order_access(id,false,true))
    WITH CHECK (kairos_security.staff_location(location_id) OR kairos_security.integration_location(location_id,true)
        OR kairos_security.worker('ORDER_EXPIRY',id));
DROP POLICY scoped_select ON order_outbox_events;
CREATE POLICY scoped_select ON order_outbox_events FOR SELECT USING (kairos_security.staff_location(location_id)
    OR kairos_security.integration_location(location_id,true) OR kairos_security.event_access(id)
    OR kairos_security.worker('ORDER_EXPIRY',order_id));
DROP POLICY scoped_insert ON order_outbox_events;
CREATE POLICY scoped_insert ON order_outbox_events FOR INSERT WITH CHECK (kairos_security.staff_location(location_id)
    OR kairos_security.integration_location(location_id,true) OR kairos_security.worker('ORDER_EXPIRY',order_id));

-- Selects the oldest unchanged active order; the claim stays locked until the expiry commits.
CREATE FUNCTION public.next_stale_order(cutoff timestamptz) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT o.id INTO selected FROM public.orders o WHERE o.status IN ('IN_PREPARATION','READY') AND o.updated_at<cutoff
        ORDER BY o.updated_at,o.id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','ORDER_EXPIRY:' || selected::text,true); END IF;
    RETURN selected;
END $$;

CREATE OR REPLACE FUNCTION public.valid_worker(operation text,resource uuid,token uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF operation IN ('ANALYTICS','WEBHOOK_FANOUT','PUSH_FANOUT','WEBHOOK_CLAIM','PUSH_CLAIM','SUBSCRIPTION_RETIRE','PUSH_CLEANUP',
        'ACCOUNT_PURGE','LOCATION_PURGE','INVITATION_PURGE','ORDER_EXPIRY') THEN
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

-- Finished webhook attempts, then outbox events nothing references any more, are kept for the retention grace period.
CREATE FUNCTION public.purge_finished_webhook_deliveries(cutoff timestamptz,batch integer) RETURNS integer
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    WITH selected AS (SELECT d.id FROM public.webhook_deliveries d
            WHERE d.status IN ('SUCCEEDED','DEAD_LETTERED') AND d.completed_at<cutoff
            ORDER BY d.completed_at,d.id LIMIT batch FOR UPDATE SKIP LOCKED),
        versions AS (DELETE FROM public.webhook_delivery_signing_versions v USING selected s WHERE v.delivery_id=s.id),
        purged AS (DELETE FROM public.webhook_deliveries d USING selected s WHERE d.id=s.id RETURNING 1)
    SELECT count(*)::integer FROM purged
$$;
CREATE FUNCTION public.purge_finished_order_events(cutoff timestamptz,batch integer) RETURNS integer
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog AS $$
    WITH purged AS (DELETE FROM public.order_outbox_events e WHERE e.id IN (
        SELECT x.id FROM public.order_outbox_events x
            WHERE x.webhook_fanout_completed_at<cutoff AND x.push_fanout_completed_at<cutoff
            AND NOT EXISTS(SELECT 1 FROM public.webhook_deliveries d WHERE d.outbox_event_id=x.id)
            AND NOT EXISTS(SELECT 1 FROM public.customer_push_deliveries d WHERE d.outbox_event_id=x.id)
        ORDER BY x.created_at,x.id LIMIT batch FOR UPDATE SKIP LOCKED) RETURNING 1)
    SELECT count(*)::integer FROM purged
$$;
CREATE INDEX order_outbox_events_finished_idx ON order_outbox_events (created_at, id)
    WHERE webhook_fanout_completed_at IS NOT NULL AND push_fanout_completed_at IS NOT NULL;

REVOKE EXECUTE ON FUNCTION public.next_stale_order(timestamptz),public.purge_finished_webhook_deliveries(timestamptz,integer),
    public.purge_finished_order_events(timestamptz,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.next_stale_order(timestamptz),public.purge_finished_webhook_deliveries(timestamptz,integer),
    public.purge_finished_order_events(timestamptz,integer) TO "${runtimeUser}";

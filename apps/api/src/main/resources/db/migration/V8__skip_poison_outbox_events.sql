-- One event whose fan-out keeps failing must not block every later event: after five failures it is marked done
-- (so the backlog metric and purge treat it as finished) and last_fanout_error keeps the reason.
ALTER TABLE order_outbox_events
    ADD COLUMN webhook_fanout_failures integer NOT NULL DEFAULT 0,
    ADD COLUMN push_fanout_failures integer NOT NULL DEFAULT 0,
    ADD COLUMN last_fanout_error text,
    ADD COLUMN last_fanout_failed_at timestamp with time zone;

CREATE OR REPLACE FUNCTION public.next_webhook_fanout() RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.order_outbox_events WHERE webhook_fanout_completed_at IS NULL AND webhook_fanout_failures<5 ORDER BY occurred_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','WEBHOOK_FANOUT:' || selected::text,true); END IF;
    RETURN selected;
END $$;
CREATE OR REPLACE FUNCTION public.next_push_fanout() RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.order_outbox_events WHERE push_fanout_completed_at IS NULL AND push_fanout_failures<5 ORDER BY occurred_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','PUSH_FANOUT:' || selected::text,true); END IF;
    RETURN selected;
END $$;

-- Runs in its own transaction after the failed one rolled back, so it cannot rely on that transaction's worker binding.
CREATE FUNCTION public.record_outbox_fanout_failure(event uuid, kind text, at_time timestamptz, error text) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF kind='WEBHOOK_FANOUT' THEN
        UPDATE public.order_outbox_events SET webhook_fanout_failures=webhook_fanout_failures+1,
            webhook_fanout_completed_at=CASE WHEN webhook_fanout_failures+1>=5 THEN at_time END,
            last_fanout_error=left(error,1000),last_fanout_failed_at=at_time
            WHERE id=event AND webhook_fanout_completed_at IS NULL;
    ELSIF kind='PUSH_FANOUT' THEN
        UPDATE public.order_outbox_events SET push_fanout_failures=push_fanout_failures+1,
            push_fanout_completed_at=CASE WHEN push_fanout_failures+1>=5 THEN at_time END,
            last_fanout_error=left(error,1000),last_fanout_failed_at=at_time
            WHERE id=event AND push_fanout_completed_at IS NULL;
    ELSE
        RETURN false;
    END IF;
    RETURN FOUND;
END $$;
REVOKE EXECUTE ON FUNCTION public.record_outbox_fanout_failure(uuid,text,timestamptz,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.record_outbox_fanout_failure(uuid,text,timestamptz,text) TO "${runtimeUser}";

-- Timeouts, 429 and 5xx answers put a webhook delivery back to PENDING with a later attempt time instead of dead-lettering it.
ALTER TABLE webhook_deliveries
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at timestamp with time zone NOT NULL DEFAULT current_timestamp;

DROP INDEX webhook_deliveries_available_idx;
CREATE INDEX webhook_deliveries_available_idx
    ON webhook_deliveries (status, next_attempt_at, claim_until, created_at, id);

CREATE OR REPLACE FUNCTION public.next_webhook_delivery(at_time timestamptz) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE selected uuid;
BEGIN
    SELECT id INTO selected FROM public.webhook_deliveries WHERE (status='PENDING' AND next_attempt_at<=at_time) OR (status='PROCESSING' AND claim_until<=at_time) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;
    IF selected IS NOT NULL THEN PERFORM set_config('kairos.selected','WEBHOOK_CLAIM:' || selected::text,true); END IF;
    RETURN selected;
END $$;

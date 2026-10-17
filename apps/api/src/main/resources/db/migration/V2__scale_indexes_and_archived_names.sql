-- Tenant-wide reports filter by tenant and hour; the primary keys lead with location.
CREATE INDEX order_hourly_analytics_tenant_hour_idx ON order_hourly_analytics(tenant_id,hour);
CREATE INDEX usage_hourly_analytics_tenant_hour_idx ON usage_hourly_analytics(tenant_id,hour);

-- Retention purges select finished rows by completion time.
CREATE INDEX webhook_deliveries_finished_idx ON webhook_deliveries(completed_at,id)
    WHERE status IN ('SUCCEEDED','DEAD_LETTERED');
CREATE INDEX email_deliveries_finished_idx ON email_deliveries(completed_at,id) WHERE state IN ('SENT','FAILED');

-- Foreign-key checks on order and event deletes would otherwise scan these tables per row.
CREATE INDEX order_outbox_events_order_idx ON order_outbox_events(order_id);
CREATE INDEX customer_push_deliveries_order_idx ON customer_push_deliveries(order_id);
CREATE INDEX customer_push_deliveries_outbox_event_idx ON customer_push_deliveries(outbox_event_id);

-- Archived integrations and subscriptions free their names, as archived locations and deleted API keys already do.
ALTER TABLE external_integrations DROP CONSTRAINT external_integrations_tenant_name_key;
CREATE UNIQUE INDEX external_integrations_tenant_live_name_key ON external_integrations(tenant_id,normalized_name)
    WHERE status <> 'ARCHIVED';
ALTER TABLE webhook_subscriptions DROP CONSTRAINT webhook_subscriptions_integration_name_key;
CREATE UNIQUE INDEX webhook_subscriptions_integration_live_name_key ON webhook_subscriptions(integration_id,normalized_name)
    WHERE status <> 'ARCHIVED';

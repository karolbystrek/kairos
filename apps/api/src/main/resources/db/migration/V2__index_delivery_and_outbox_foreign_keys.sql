-- Subscription retirement and location purge filter on these columns without a leading index.
CREATE INDEX customer_push_deliveries_subscription_idx ON customer_push_deliveries (subscription_id);
CREATE INDEX webhook_deliveries_subscription_idx ON webhook_deliveries (subscription_id);
CREATE INDEX order_outbox_events_location_idx ON order_outbox_events (location_id);

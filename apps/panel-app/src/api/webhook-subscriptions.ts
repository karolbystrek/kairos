import { z } from "zod";

import { apiFetch, request } from "./api-fetch";
import { managedIntegrationNameSchema } from "./integrations";

export const webhookEventTypeSchema = z.enum([
  "order.created",
  "order.ready",
  "order.completed",
  "order.canceled",
]);

export const webhookSigningSecretVersionSchema = z.object({
  id: z.uuid(),
  issuedAt: z.iso.datetime({ offset: true }),
  validUntil: z.iso.datetime({ offset: true }).nullable(),
  retiredAt: z.iso.datetime({ offset: true }).nullable(),
});

export const webhookSubscriptionSchema = z.object({
  id: z.uuid(),
  integrationId: z.uuid(),
  name: z.string(),
  destinationUrl: z.url(),
  status: z.enum(["ENABLED", "DISABLED", "ARCHIVED"]),
  locationIds: z.array(z.uuid()),
  eventTypes: z.array(webhookEventTypeSchema),
  signingSecretVersions: z.array(webhookSigningSecretVersionSchema),
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

const webhookSubscriptionsSchema = z.array(webhookSubscriptionSchema);

const webhookSubscriptionInputSchema = z.object({
  name: managedIntegrationNameSchema,
  destinationUrl: z
    .url("Destination must be a valid URL")
    .max(2048, "Destination must not exceed 2048 characters"),
  locationIds: z.array(z.uuid()).min(1, "Choose at least one location"),
  eventTypes: z
    .array(webhookEventTypeSchema)
    .min(1, "Choose at least one event"),
});

const issuedWebhookSubscriptionSchema = z.object({
  subscription: webhookSubscriptionSchema,
  signingSecret: z.string().min(1),
});

const issuedWebhookSigningSecretSchema = z.object({
  version: webhookSigningSecretVersionSchema,
  signingSecret: z.string().min(1),
});

export type WebhookEventType = z.infer<typeof webhookEventTypeSchema>;
export type WebhookSigningSecretVersion = z.infer<
  typeof webhookSigningSecretVersionSchema
>;
export type WebhookSubscription = z.infer<typeof webhookSubscriptionSchema>;
export type WebhookSubscriptionInput = z.input<
  typeof webhookSubscriptionInputSchema
>;
export type IssuedWebhookSubscription = z.infer<
  typeof issuedWebhookSubscriptionSchema
>;
export type IssuedWebhookSigningSecret = z.infer<
  typeof issuedWebhookSigningSecretSchema
>;

export function listWebhookSubscriptions(
  integrationId: string,
): Promise<WebhookSubscription[]> {
  const search = new URLSearchParams({ integrationId });

  return request(
    `/api/webhook-subscriptions/v1?${search}`,
    webhookSubscriptionsSchema,
  );
}

export function createWebhookSubscription(
  integrationId: string,
  input: WebhookSubscriptionInput,
): Promise<IssuedWebhookSubscription> {
  const validated = webhookSubscriptionInputSchema.parse(input);

  return request(
    "/api/webhook-subscriptions/v1",
    issuedWebhookSubscriptionSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ integrationId, ...validated }),
    },
  );
}

export function updateWebhookSubscription(
  subscriptionId: string,
  input: WebhookSubscriptionInput,
): Promise<WebhookSubscription> {
  const validated = webhookSubscriptionInputSchema.parse(input);

  return request(
    `/api/webhook-subscriptions/v1/${subscriptionId}`,
    webhookSubscriptionSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(validated),
    },
  );
}

export function updateWebhookSubscriptionStatus(
  subscriptionId: string,
  status: "ENABLED" | "DISABLED",
): Promise<WebhookSubscription> {
  return request(
    `/api/webhook-subscriptions/v1/${subscriptionId}/status`,
    webhookSubscriptionSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    },
  );
}

export async function archiveWebhookSubscription(
  subscriptionId: string,
): Promise<void> {
  await apiFetch(`/api/webhook-subscriptions/v1/${subscriptionId}`, {
    method: "DELETE",
  });
}

export function rotateWebhookSigningSecret(
  subscriptionId: string,
): Promise<IssuedWebhookSigningSecret> {
  return request(
    "/api/webhook-signing-secrets/v1",
    issuedWebhookSigningSecretSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ subscriptionId }),
    },
  );
}

export function retireWebhookSigningSecret(
  subscriptionId: string,
  versionId: string,
): Promise<WebhookSigningSecretVersion> {
  return request(
    `/api/webhook-signing-secrets/v1/${versionId}/retirement`,
    webhookSigningSecretVersionSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ subscriptionId }),
    },
  );
}

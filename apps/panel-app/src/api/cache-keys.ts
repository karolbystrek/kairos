export const staffCachePrefix = "staff";

export const staffLocationsKey = (accountId: string) =>
  [staffCachePrefix, accountId, "locations"] as const;

export const staffIntegrationsKey = (accountId: string) =>
  [staffCachePrefix, accountId, "external-integrations"] as const;

export const staffApiKeysKey = (accountId: string, integrationId: string) =>
  [staffCachePrefix, accountId, "api-keys", integrationId] as const;

export const staffApiKeyVersionsKey = (accountId: string, apiKeyId: string) =>
  [staffCachePrefix, accountId, "api-key-versions", apiKeyId] as const;

export const staffWebhookSubscriptionsKey = (
  accountId: string,
  integrationId: string,
) =>
  [
    staffCachePrefix,
    accountId,
    "webhook-subscriptions",
    integrationId,
  ] as const;

export function isStaffCacheKey(key: unknown): boolean {
  return Array.isArray(key) && key[0] === staffCachePrefix;
}

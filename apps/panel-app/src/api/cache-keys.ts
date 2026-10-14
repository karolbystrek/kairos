export const staffCachePrefix = "staff";
export const currentAccountKey = ["authentication", "current-account"] as const;

export const staffLocationsKey = (accountId: string) =>
  [staffCachePrefix, accountId, "locations"] as const;

export const staffLocationMenuKey = (accountId: string, locationId: string) =>
  [staffCachePrefix, accountId, "location-menu", locationId] as const;

export const staffAccountsKey = (accountId: string) =>
  [staffCachePrefix, accountId, "managed-accounts"] as const;

export const staffInvitationsKey = (accountId: string) =>
  [staffCachePrefix, accountId, "account-invitations"] as const;

export const staffIntegrationsKey = (accountId: string) =>
  [staffCachePrefix, accountId, "external-integrations"] as const;

export const staffApiKeysKey = (accountId: string, integrationId: string) =>
  [staffCachePrefix, accountId, "api-keys", integrationId] as const;

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

export const staffAnalyticsKey = (
  accountId: string,
  scope: "restaurant" | "platform",
  from: string,
  to: string,
  zone: string,
  locationId = "tenant",
) =>
  [
    staffCachePrefix,
    accountId,
    "analytics",
    scope,
    locationId,
    from,
    to,
    zone,
  ] as const;

export const staffPlatformHealthKey = (accountId: string) =>
  [staffCachePrefix, accountId, "platform-health"] as const;

export function isStaffCacheKey(key: unknown): boolean {
  return Array.isArray(key) && key[0] === staffCachePrefix;
}

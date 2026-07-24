export const staffCachePrefix = "staff";

export const staffLocationsKey = (accountId: string) =>
  [staffCachePrefix, accountId, "locations"] as const;

export function isStaffCacheKey(key: unknown): boolean {
  return Array.isArray(key) && key[0] === staffCachePrefix;
}

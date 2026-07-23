export const staffCachePrefix = "staff";

export function isStaffCacheKey(key: unknown): boolean {
  return Array.isArray(key) && key[0] === staffCachePrefix;
}

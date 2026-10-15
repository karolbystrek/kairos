import useSWR from "swr";
import { z } from "zod";

import { apiFetch, request } from "./api-fetch";
import { staffLocationsKey } from "./cache-keys";
import { browserTimeZone } from "./report-range";

export const managedLocationNameSchema = z
  .string()
  .trim()
  .min(1, "Podaj nazwę.")
  .refine(
    (name) => Array.from(name).length <= 120,
    "Nazwa może mieć maksymalnie 120 znaków.",
  )
  .refine(
    (name) => !/[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(name),
    "Użyj jednej linii tekstu.",
  );

// Mirrors the API's accepted hosts for early feedback; the API stays authoritative.
const googleReviewHosts = new Set([
  "g.page",
  "maps.app.goo.gl",
  "search.google.com",
  "www.google.com",
  "maps.google.com",
]);

function isGoogleReviewUrl(value: string): boolean {
  try {
    const url = new URL(value);

    return (
      url.protocol === "https:" &&
      googleReviewHosts.has(url.hostname) &&
      !url.username &&
      !url.password &&
      !value.includes("#")
    );
  } catch {
    return false;
  }
}

export const googleReviewUrlSchema = z
  .string()
  .trim()
  .max(2048, "Link może mieć maksymalnie 2048 znaków.")
  .refine(
    (url) => url === "" || isGoogleReviewUrl(url),
    "Użyj linku HTTPS do Map Google lub opinii Google.",
  );

const locationSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  timeZone: z.string(),
  status: z.enum(["ENABLED", "DISABLED"]),
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
  googleReviewUrl: z.string().nullable().optional(),
});

const locationsSchema = z.array(locationSchema);

export type Location = z.infer<typeof locationSchema>;

export function sortLocations(locations: Location[]): Location[] {
  return [...locations].sort((first, second) => {
    if (first.status !== second.status) {
      return first.status === "ENABLED" ? -1 : 1;
    }

    return (
      first.name.localeCompare(second.name, "pl-PL", {
        sensitivity: "base",
      }) || first.id.localeCompare(second.id)
    );
  });
}

function listLocations(): Promise<Location[]> {
  return request("/api/locations/v1", locationsSchema);
}

// Pass no account to skip loading.
export function useStaffLocations(accountId?: string) {
  const result = useSWR(
    accountId ? staffLocationsKey(accountId) : null,
    listLocations,
  );
  const enabledLocations = (result.data ?? []).filter(
    (location) => location.status === "ENABLED",
  );

  // Assigning keeps SWR's lazy getters, so callers subscribe only to fields they read.
  return Object.assign(result, { enabledLocations });
}

function locationMutation(
  path: string,
  method: "POST" | "PUT",
  body: unknown,
): Promise<Location> {
  return request(path, locationSchema, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function createLocation(
  name: string,
  googleReviewUrl?: string,
): Promise<Location> {
  return locationMutation("/api/locations/v1", "POST", {
    name: managedLocationNameSchema.parse(name),
    googleReviewUrl: googleReviewUrl?.trim() || null,
    // The location's business day follows the creating browser's time zone.
    timeZone: browserTimeZone(),
  });
}

export function renameLocation(
  locationId: string,
  name: string,
): Promise<Location> {
  return locationMutation(
    `/api/locations/v1/${encodeURIComponent(locationId)}`,
    "PUT",
    { name: managedLocationNameSchema.parse(name) },
  );
}

export function updateLocationStatus(
  locationId: string,
  status: "ENABLED" | "DISABLED",
): Promise<Location> {
  return locationMutation(
    `/api/locations/v1/${encodeURIComponent(locationId)}/status`,
    "PUT",
    { status },
  );
}

export async function deleteLocation(locationId: string): Promise<boolean> {
  await apiFetch(`/api/locations/v1/${encodeURIComponent(locationId)}`, {
    method: "DELETE",
  });

  return true;
}

export function updateLocationReviewLink(
  locationId: string,
  googleReviewUrl: string,
): Promise<Location> {
  return locationMutation(
    `/api/locations/v1/${encodeURIComponent(locationId)}/reviews`,
    "PUT",
    {
      googleReviewUrl: googleReviewUrl.trim() || null,
    },
  );
}

import { z } from "zod";

import { apiFetch, request } from "./api-fetch";

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

export const locationSchema = z.object({
  id: z.uuid(),
  name: z.string(),
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

export function listLocations(): Promise<Location[]> {
  return request("/api/locations/v1", locationsSchema);
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

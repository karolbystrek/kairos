import { z } from "zod";

import { request } from "./api-fetch";

export const locationSchema = z.object({
  id: z.uuid(),
});

const locationsSchema = z.array(locationSchema);

export type Location = z.infer<typeof locationSchema>;

export function listLocations(): Promise<Location[]> {
  return request("/api/locations/v1", locationsSchema);
}

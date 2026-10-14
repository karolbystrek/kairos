import { z } from "zod";

import { apiFetch, request } from "./api-fetch";

const singleLinePattern = /[\u0000-\u001f\u007f-\u009f\u2028\u2029]/;

export const menuItemsSchema = z
  .array(
    z
      .string()
      .trim()
      .min(1)
      .refine(
        (item) => Array.from(item).length <= 60,
        "Nazwa pozycji może mieć maksymalnie 60 znaków.",
      )
      .refine(
        (item) => !singleLinePattern.test(item),
        "Nazwa pozycji musi być jedną linią tekstu.",
      ),
  )
  .min(1, "Dodaj co najmniej jedną pozycję.")
  .max(100, "Menu może mieć maksymalnie 100 pozycji.")
  .refine(
    (items) =>
      new Set(items.map((item) => item.toLocaleLowerCase("pl-PL"))).size ===
      items.length,
    "Nazwy pozycji muszą się różnić.",
  );

const menuSchema = z.object({
  items: z.array(z.string()),
  updatedAt: z.iso.datetime({ offset: true }),
});

const locationMenuSchema = z.object({
  menu: menuSchema.nullable(),
});

export type Menu = z.infer<typeof menuSchema>;

// One item per line keeps menu editing to a single text area.
export function parseMenuItems(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function locationMenuPath(locationId: string): string {
  return `/api/locations/v1/${encodeURIComponent(locationId)}/menu`;
}

export async function getLocationMenu(
  locationId: string,
): Promise<Menu | null> {
  const result = await request(
    locationMenuPath(locationId),
    locationMenuSchema,
  );

  return result.menu;
}

export async function saveLocationMenu(
  locationId: string,
  items: string[],
): Promise<Menu | null> {
  const result = await request(
    locationMenuPath(locationId),
    locationMenuSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items: menuItemsSchema.parse(items) }),
    },
  );

  return result.menu;
}

export async function deleteLocationMenu(locationId: string): Promise<null> {
  await apiFetch(locationMenuPath(locationId), { method: "DELETE" });

  return null;
}

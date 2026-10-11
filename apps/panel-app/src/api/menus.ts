import { z } from "zod";

import { apiFetch, request } from "./api-fetch";

const singleLinePattern = /[\u0000-\u001f\u007f-\u009f\u2028\u2029]/;

export const menuNameSchema = z
  .string()
  .trim()
  .min(1, "Podaj nazwę menu.")
  .refine(
    (name) => Array.from(name).length <= 120,
    "Nazwa może mieć maksymalnie 120 znaków.",
  )
  .refine((name) => !singleLinePattern.test(name), "Użyj jednej linii tekstu.");

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

export const menuSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  items: z.array(z.string()),
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

const menusSchema = z.array(menuSchema);

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

export function listMenus(): Promise<Menu[]> {
  return request("/api/menus/v1", menusSchema);
}

function menuMutation(
  path: string,
  method: "POST" | "PUT",
  name: string,
  items: string[],
): Promise<Menu> {
  return request(path, menuSchema, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name: menuNameSchema.parse(name),
      items: menuItemsSchema.parse(items),
    }),
  });
}

export function createMenu(name: string, items: string[]): Promise<Menu> {
  return menuMutation("/api/menus/v1", "POST", name, items);
}

export function updateMenu(
  menuId: string,
  name: string,
  items: string[],
): Promise<Menu> {
  return menuMutation(
    `/api/menus/v1/${encodeURIComponent(menuId)}`,
    "PUT",
    name,
    items,
  );
}

export async function deleteMenu(menuId: string): Promise<boolean> {
  await apiFetch(`/api/menus/v1/${encodeURIComponent(menuId)}`, {
    method: "DELETE",
  });

  return true;
}

export async function getLocationMenu(
  locationId: string,
): Promise<Menu | null> {
  const result = await request(
    `/api/locations/v1/${encodeURIComponent(locationId)}/menu`,
    locationMenuSchema,
  );

  return result.menu;
}

export async function setLocationMenu(
  locationId: string,
  menuId: string | null,
): Promise<Menu | null> {
  const result = await request(
    `/api/locations/v1/${encodeURIComponent(locationId)}/menu`,
    locationMenuSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ menuId }),
    },
  );

  return result.menu;
}

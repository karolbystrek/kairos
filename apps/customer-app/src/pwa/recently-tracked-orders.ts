import { z } from "zod";

import { orderStatusSchema, type OrderStatus } from "@/src/orders/order-status";

const RECENTLY_TRACKED_ORDERS_STORAGE_KEY = "kairos.recently-tracked-orders";
const CONSUMED_INSTALLATION_BOOTSTRAPS_STORAGE_KEY =
  "kairos.consumed-installation-bootstraps";

const recentlyTrackedOrderSchema = z
  .object({
    trackingReference: z.string().min(1),
    label: z.string(),
    status: orderStatusSchema,
    updatedAt: z.iso.datetime({ offset: true }),
  })
  .strict();

const recentlyTrackedOrdersPayloadSchema = z
  .object({
    version: z.literal(1),
    orders: z.array(recentlyTrackedOrderSchema),
  })
  .strict();

const consumedInstallationBootstrapsPayloadSchema = z
  .object({
    version: z.literal(1),
    trackingReferences: z.array(z.string().min(1)),
  })
  .strict();

export type RecentlyTrackedOrder = z.infer<typeof recentlyTrackedOrderSchema>;

function getLocalStorage(): Storage | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function parseStoredValue<T>(
  value: string | null,
  schema: z.ZodType<T>,
): T | null {
  if (value === null) {
    return null;
  }

  try {
    const result = schema.safeParse(JSON.parse(value));

    return result.success ? result.data : null;
  } catch {
    return null;
  }
}

export function readRecentlyTrackedOrders(): RecentlyTrackedOrder[] {
  const storage = getLocalStorage();

  if (!storage) {
    return [];
  }

  try {
    return (
      parseStoredValue(
        storage.getItem(RECENTLY_TRACKED_ORDERS_STORAGE_KEY),
        recentlyTrackedOrdersPayloadSchema,
      )?.orders ?? []
    );
  } catch {
    return [];
  }
}

export function rememberTrackedOrder(order: {
  trackingReference: string;
  label: string;
  status: OrderStatus;
  updatedAt: string;
}): void {
  const parsedOrder = recentlyTrackedOrderSchema.safeParse(order);
  const storage = getLocalStorage();

  if (!parsedOrder.success || !storage) {
    return;
  }

  const orders = readRecentlyTrackedOrders().filter(
    ({ trackingReference }) =>
      trackingReference !== parsedOrder.data.trackingReference,
  );

  try {
    storage.setItem(
      RECENTLY_TRACKED_ORDERS_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        orders: [parsedOrder.data, ...orders],
      }),
    );
  } catch {
    // Browser storage is best effort and never blocks authoritative tracking.
  }
}

export function clearRecentlyTrackedOrders(): void {
  const storage = getLocalStorage();

  if (!storage) {
    return;
  }

  try {
    storage.removeItem(RECENTLY_TRACKED_ORDERS_STORAGE_KEY);
  } catch {
    // Home presents an empty in-memory view even when storage is inaccessible.
  }
}

export function consumeInstallationBootstrap(
  trackingReference: string | null,
): string | null {
  if (!trackingReference) {
    return null;
  }

  const storage = getLocalStorage();

  if (!storage) {
    return null;
  }

  try {
    const storedPayload = parseStoredValue(
      storage.getItem(CONSUMED_INSTALLATION_BOOTSTRAPS_STORAGE_KEY),
      consumedInstallationBootstrapsPayloadSchema,
    );
    const consumedTrackingReferences = storedPayload?.trackingReferences ?? [];

    if (consumedTrackingReferences.includes(trackingReference)) {
      return null;
    }

    storage.setItem(
      CONSUMED_INSTALLATION_BOOTSTRAPS_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        trackingReferences: [trackingReference, ...consumedTrackingReferences],
      }),
    );

    return trackingReference;
  } catch {
    return null;
  }
}

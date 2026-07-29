import { z } from "zod";

import {
  isActiveOrderStatus,
  orderStatusSchema,
} from "@/src/orders/order-status";
import { rememberTrackedOrder } from "@/src/pwa/storage";

const RECENTLY_TRACKED_ORDERS_STORAGE_KEY = "kairos.recently-tracked-orders";
const CONSUMED_INSTALLATION_BOOTSTRAPS_STORAGE_KEY =
  "kairos.consumed-installation-bootstraps";

const legacyTrackedOrdersPayloadSchema = z
  .object({
    version: z.literal(1),
    orders: z.array(
      z
        .object({
          trackingReference: z.string().min(1),
          label: z.string(),
          status: orderStatusSchema,
          updatedAt: z.iso.datetime({ offset: true }),
        })
        .strict(),
    ),
  })
  .strict();

const consumedInstallationBootstrapsPayloadSchema = z
  .object({
    version: z.literal(1),
    trackingReferences: z.array(z.string().min(1)),
  })
  .strict();

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

export async function migrateLegacyRecentlyTrackedOrders(): Promise<void> {
  const storage = getLocalStorage();

  if (!storage) {
    return;
  }

  try {
    const payload = parseStoredValue(
      storage.getItem(RECENTLY_TRACKED_ORDERS_STORAGE_KEY),
      legacyTrackedOrdersPayloadSchema,
    );

    for (const order of payload?.orders ?? []) {
      if (isActiveOrderStatus(order.status)) {
        await rememberTrackedOrder(order);
      }
    }
    storage.removeItem(RECENTLY_TRACKED_ORDERS_STORAGE_KEY);
  } catch {
    // Invalid, inaccessible, and incompatible legacy storage is discarded.
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

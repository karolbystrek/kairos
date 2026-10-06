import { describe, expect, it } from "vitest";

import { resolveInstalledLaunchHref } from "@/src/pwa/launch-destination";
import type { StoredTrackedOrder } from "@/src/pwa/storage";

const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
const ORDER_HREF = `/orders/${TRACKING_REFERENCE}`;
const ACTIVE_ORDER: StoredTrackedOrder = {
  label: "A12",
  rememberedAt: 1,
  status: "IN_PREPARATION",
  trackingReference: TRACKING_REFERENCE,
  updatedAt: "2026-08-05T10:00:00Z",
};

describe("installed launch restoration", () => {
  it("preserves the one-time installation order bootstrap", () => {
    expect(
      resolveInstalledLaunchHref({
        installationTrackingReference: TRACKING_REFERENCE,
        lastDestination: { kind: "home" },
        orders: [],
      }),
    ).toBe(ORDER_HREF);
  });

  it("reopens the last active order when it was left open", () => {
    expect(
      resolveInstalledLaunchHref({
        installationTrackingReference: null,
        lastDestination: {
          kind: "order",
          trackingReference: TRACKING_REFERENCE,
        },
        orders: [ACTIVE_ORDER],
      }),
    ).toBe(ORDER_HREF);
  });

  it("stays on Home when Home was last or the order is no longer active", () => {
    expect(
      resolveInstalledLaunchHref({
        installationTrackingReference: null,
        lastDestination: { kind: "home" },
        orders: [ACTIVE_ORDER],
      }),
    ).toBe("/");
    for (const orders of [
      [],
      [{ ...ACTIVE_ORDER, status: "COMPLETED" as const }],
      [{ ...ACTIVE_ORDER, status: "CANCELED" as const }],
    ]) {
      expect(
        resolveInstalledLaunchHref({
          installationTrackingReference: null,
          lastDestination: {
            kind: "order",
            trackingReference: TRACKING_REFERENCE,
          },
          orders,
        }),
      ).toBe("/");
    }
  });
});

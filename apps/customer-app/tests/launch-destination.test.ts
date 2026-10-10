import "fake-indexeddb/auto";

import { describe, expect, it } from "vitest";

import { resolveCustomerLaunchHref } from "@/src/pwa/launch-destination";
import {
  readLastStableDestination,
  readTrackedOrder,
  rememberLastStableDestination,
  rememberTrackedOrder,
} from "@/src/pwa/storage";

const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
const ORDER_HREF = `/orders/${TRACKING_REFERENCE}`;

async function launchHref() {
  return resolveCustomerLaunchHref({
    installationTrackingReference: null,
    lastDestination: await readLastStableDestination(),
  });
}

describe("customer launch restoration", () => {
  it("preserves the one-time installation order bootstrap", () => {
    expect(
      resolveCustomerLaunchHref({
        installationTrackingReference: TRACKING_REFERENCE,
        lastDestination: { kind: "home" },
      }),
    ).toBe(ORDER_HREF);
  });

  it("shows the scanner page without an unclosed order", () => {
    for (const lastDestination of [null, { kind: "home" as const }]) {
      expect(
        resolveCustomerLaunchHref({
          installationTrackingReference: null,
          lastDestination,
        }),
      ).toBe("/");
    }
  });

  it.each(["IN_PREPARATION", "READY", "COMPLETED", "CANCELED"] as const)(
    "restores an unclosed %s order and returns to scanning after closing",
    async (status) => {
      await rememberLastStableDestination({
        kind: "order",
        trackingReference: TRACKING_REFERENCE,
      });
      await rememberTrackedOrder({
        label: "A12",
        status,
        trackingReference: TRACKING_REFERENCE,
        updatedAt: "2026-10-09T20:00:00Z",
      });

      if (status === "COMPLETED" || status === "CANCELED") {
        expect(await readTrackedOrder(TRACKING_REFERENCE)).toBeNull();
      }
      expect(await launchHref()).toBe(ORDER_HREF);

      await rememberLastStableDestination({ kind: "home" });
      expect(await launchHref()).toBe("/");
    },
  );
});

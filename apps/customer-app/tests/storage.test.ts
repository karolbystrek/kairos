import "fake-indexeddb/auto";

import { beforeEach, describe, expect, it } from "vitest";

import {
  readLastStableDestination,
  readNotificationMetadata,
  readTrackedOrder,
  rememberLastStableDestination,
  rememberTrackedOrder,
  removeTrackedOrder,
  updateNotificationMetadata,
} from "@/src/pwa/storage";

const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";

function deleteCustomerDatabase(): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase("kairos-customer");

    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error("Database deletion blocked."));
  });
}

beforeEach(async () => {
  await deleteCustomerDatabase();
});

describe("single-order tracking storage", () => {
  it("removes just one order and permits explicit retracking", async () => {
    await updateNotificationMetadata({
      enrolledTrackingReferences: [TRACKING_REFERENCE],
      notificationsEnabled: true,
    });
    await rememberTrackedOrder({
      label: "A12",
      status: "IN_PREPARATION",
      trackingReference: TRACKING_REFERENCE,
      updatedAt: "2026-08-05T10:00:00Z",
    });

    expect(
      await removeTrackedOrder(TRACKING_REFERENCE, "suppressed"),
    ).toBe(true);

    expect(await readTrackedOrder(TRACKING_REFERENCE)).toBeNull();
    expect(
      (await readNotificationMetadata()).enrolledTrackingReferences,
    ).toEqual([]);

    await rememberTrackedOrder({
      label: "A12",
      status: "READY",
      trackingReference: TRACKING_REFERENCE,
      updatedAt: "2026-08-05T10:05:00Z",
    });

    expect((await readTrackedOrder(TRACKING_REFERENCE))?.status).toBe("READY");
  });
});

describe("last stable destination", () => {
  it("stores Home and active order destinations", async () => {
    await rememberLastStableDestination({ kind: "home" });
    expect(await readLastStableDestination()).toEqual({ kind: "home" });

    await rememberLastStableDestination({
      kind: "order",
      trackingReference: TRACKING_REFERENCE,
    });
    expect(await readLastStableDestination()).toEqual({
      kind: "order",
      trackingReference: TRACKING_REFERENCE,
    });
  });
});

import "fake-indexeddb/auto";

import { beforeEach, expect, it } from "vitest";

import { readTrackedOrder, rememberTrackedOrder } from "@/src/pwa/storage";

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

it("does not let stale REST persistence undo a ready Push or stream state", async () => {
  const order = { trackingReference: TRACKING_REFERENCE, number: 12, label: "A12", updatedAt: "2026-10-12T10:00:00Z" };
  await rememberTrackedOrder({ ...order, status: "READY" });
  await rememberTrackedOrder({ ...order, status: "IN_PREPARATION" });
  expect((await readTrackedOrder(TRACKING_REFERENCE))?.status).toBe("READY");
});

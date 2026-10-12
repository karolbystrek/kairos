import "fake-indexeddb/auto";

import { beforeEach, describe, expect, it } from "vitest";

import {
  readLastStableDestination,
  readTrackedOrder,
  rememberTrackedOrder,
  rememberLastStableDestination,
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

it("does not let stale REST persistence undo a ready Push or stream state", async () => {
  const order = { trackingReference: TRACKING_REFERENCE, label: "A12", updatedAt: "2026-10-12T10:00:00Z" };
  await rememberTrackedOrder({ ...order, status: "READY" });
  await rememberTrackedOrder({ ...order, status: "IN_PREPARATION" });
  expect((await readTrackedOrder(TRACKING_REFERENCE))?.status).toBe("READY");
});

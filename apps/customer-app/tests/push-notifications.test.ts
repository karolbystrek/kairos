import "fake-indexeddb/auto";

import { afterAll, beforeAll, beforeEach, expect, it, vi } from "vitest";

import {
  readNotificationMetadata,
  readNotificationTrackingReferences,
  readTrackedOrder,
  rememberTrackedOrder,
  removeTrackedOrder,
  updateNotificationMetadata,
} from "@/src/pwa/storage";

vi.mock("serwist", () => ({
  NavigationRoute: class {},
  NetworkOnly: class {},
  Serwist: class {
    registerCapture() {}
    registerRoute() {}
    setCatchHandler() {}
    addEventListeners() {}
  },
}));

const REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
const TRANSITION_TIME = "2026-10-09T20:00:00Z";
const payload = {
  version: 1,
  eventId: "060f9c9d-1762-4fd2-a5a8-c11ddc00ff21",
  trackingReference: REFERENCE,
  status: "READY",
  transitionedAt: TRANSITION_TIME,
  orderUrl: `/orders/${REFERENCE}`,
};
const listeners = new Map<string, (event: unknown) => void>();
const showNotification = vi.fn().mockResolvedValue(undefined);
const openWindow = vi.fn().mockResolvedValue(undefined);

beforeAll(async () => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.example");
  vi.stubGlobal("self", {
    location: { origin: "https://customer.kairos.example" },
    addEventListener: (type: string, listener: (event: unknown) => void) =>
      listeners.set(type, listener),
    registration: { showNotification },
    clients: { matchAll: async () => [], openWindow },
  });
  await import("../app/sw");
});

afterAll(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

beforeEach(async () => {
  vi.clearAllMocks();
  await new Promise<void>((resolve, reject) => {
    const request = indexedDB.deleteDatabase("kairos-customer");

    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
  await updateNotificationMetadata({
    enrolledTrackingReferences: [REFERENCE],
    notificationsEnabled: true,
  });
  await rememberTrackedOrder({
    label: "Private order label",
    status: "IN_PREPARATION",
    trackingReference: REFERENCE,
    updatedAt: "2026-10-09T19:55:00Z",
  });
});

async function dispatch(type: string, event: object): Promise<void> {
  let completion: Promise<void> | undefined;

  listeners.get(type)?.({
    ...event,
    waitUntil: (work: Promise<void>) => {
      completion = work;
    },
  });
  await completion;
}

async function push(candidate: unknown = payload): Promise<void> {
  await dispatch("push", { data: { json: () => candidate } });
}

it("shows a background transition once and opens its order on click", async () => {
  await push();
  await push();

  expect(showNotification).toHaveBeenCalledExactlyOnceWith("Kairos", {
    body: "Your order is ready for pickup",
    tag: `kairos-order-${REFERENCE}`,
    data: { eventId: payload.eventId, orderUrl: `/orders/${REFERENCE}` },
  });
  expect((await readTrackedOrder(REFERENCE))?.status).toBe("READY");
  await dispatch("notificationclick", {
    notification: {
      close: () => {},
      data: { orderUrl: `/orders/${REFERENCE}` },
    },
  });
  expect(openWindow).toHaveBeenCalledWith(
    `https://customer.kairos.example/orders/${REFERENCE}`,
  );
});

it.each(["READY", "COMPLETED", "CANCELED"] as const)(
  "shows %s once when REST stores the transition before Push arrives",
  async (status) => {
    await rememberTrackedOrder({
      label: "Private order label",
      status,
      trackingReference: REFERENCE,
      updatedAt: TRANSITION_TIME,
    });
    await rememberTrackedOrder({
      label: "Private order label",
      status,
      trackingReference: REFERENCE,
      updatedAt: TRANSITION_TIME,
    });
    // App reconciliation can already have removed terminal enrollments.
    if (status !== "READY") {
      await updateNotificationMetadata({ enrolledTrackingReferences: [] });
    }
    expect(await readNotificationTrackingReferences()).toEqual([REFERENCE]);
    await push({ ...payload, status });
    await push({ ...payload, status });

    expect(showNotification).toHaveBeenCalledTimes(1);
    expect(showNotification.mock.calls[0][1].data.orderUrl).toBe(
      `/orders/${REFERENCE}`,
    );
    expect((await readNotificationMetadata()).enrolledTrackingReferences).toEqual(
      status === "READY" ? [REFERENCE] : [],
    );
    expect(await readNotificationTrackingReferences()).toEqual(
      status === "READY" ? [REFERENCE] : [],
    );
  },
);

it("suppresses an older Ready push after a terminal REST transition", async () => {
  await rememberTrackedOrder({
    label: "Private order label",
    status: "COMPLETED",
    trackingReference: REFERENCE,
    updatedAt: "2026-10-09T20:01:00Z",
  });
  await push();

  expect(showNotification).not.toHaveBeenCalled();
  expect(await readTrackedOrder(REFERENCE)).toBeNull();
});

it("does not notify after explicit tracking removal", async () => {
  await removeTrackedOrder(REFERENCE, "suppressed");
  await push();

  expect(showNotification).not.toHaveBeenCalled();
});

it("does not notify after opt-out", async () => {
  await updateNotificationMetadata({ notificationsEnabled: false });
  await push();

  expect(showNotification).not.toHaveBeenCalled();
});

it("does not restore a pending final notification after disabling and re-enabling", async () => {
  await rememberTrackedOrder({
    label: "Private order label",
    status: "COMPLETED",
    trackingReference: REFERENCE,
    updatedAt: TRANSITION_TIME,
  });
  await updateNotificationMetadata({
    notificationsEnabled: false,
    enrolledTrackingReferences: [],
  });
  await updateNotificationMetadata({ notificationsEnabled: true });
  await push({ ...payload, status: "COMPLETED" });

  expect(showNotification).not.toHaveBeenCalled();
});

it("shows a privacy-preserving fallback for an invalid payload", async () => {
  await push({ label: "Private order label" });

  expect(showNotification).toHaveBeenCalledExactlyOnceWith("Kairos", {
    body: "Your order status changed. Open Kairos for the latest status.",
    tag: "kairos-generic-order-update",
    data: { orderUrl: "/" },
  });
});

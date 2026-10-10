import "fake-indexeddb/auto";

import { afterEach, expect, it, vi } from "vitest";

import { updateNotificationMetadata } from "@/src/pwa/storage";

// Serwist owns precaching; this scenario exercises Kairos's Push and click handlers.
vi.mock("serwist", () => ({
  NetworkOnly: class {},
  NavigationRoute: class {},
  Serwist: class {
    registerCapture() {}
    registerRoute() {}
    setCatchHandler() {}
    addEventListeners() {}
  },
}));

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

it("displays one review notification and opens its in-app prompt on click", async () => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.test");
  const listeners = new Map<string, (event: unknown) => void>();
  const notifications: { title: string; options: NotificationOptions }[] = [];
  const opened: string[] = [];
  vi.stubGlobal("self", {
    location: { origin: "https://customer.kairos.test" },
    registration: { showNotification: async (title: string, options: NotificationOptions) => { notifications.push({ title, options }); } },
    clients: { matchAll: async () => [], openWindow: async (url: string) => { opened.push(url); } },
    addEventListener: (type: string, listener: (event: unknown) => void) => listeners.set(type, listener),
  });
  await import("../app/sw");
  await updateNotificationMetadata({ notificationsEnabled: true });
  const reference = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
  const payload = { version: 2, kind: "REVIEW", eventId: "060f9c9d-1762-4fd2-a5a8-c11ddc00ff21", trackingReference: reference, dueAt: "2026-01-01T10:00:00Z", orderUrl: `/?review=${reference}` };
  async function dispatch(type: string, event: object) {
    let pending: Promise<void> | undefined;
    listeners.get(type)?.({ ...event, waitUntil: (promise: Promise<void>) => { pending = promise; } });
    await pending;
  }
  await dispatch("push", { data: { json: () => payload } });
  await dispatch("push", { data: { json: () => payload } });
  expect(notifications).toHaveLength(1);
  expect(notifications[0].options.tag).toBe(`kairos-review-${reference}`);
  await dispatch("notificationclick", { notification: { close() {}, data: notifications[0].options.data } });
  expect(opened).toEqual([`https://customer.kairos.test/?review=${reference}`]);
});

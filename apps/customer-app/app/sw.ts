/// <reference lib="webworker" />

import type { OrderStatus } from "@/src/orders/order-status";
import type { PrecacheEntry, SerwistGlobalConfig } from "serwist";

import { NavigationRoute, NetworkOnly, Serwist } from "serwist";
import * as z from "zod/mini";

import { apiOrigin, apiUrl } from "@/src/api/api-url";
import { updateApplicationBadge } from "@/src/pwa/badge";
import {
  applyPushTransition,
  readNotificationMetadata,
  updateNotificationMetadata,
  type SerializedPushSubscription,
} from "@/src/pwa/storage";

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }
}

declare const self: ServiceWorkerGlobalScope;

type PushSubscriptionChangeEvent = ExtendableEvent & {
  newSubscription?: PushSubscription | null;
  oldSubscription?: PushSubscription | null;
};

const CSRF_COOKIE_NAME = "__Host-XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CSRF_PROBLEM_TYPES = new Set([
  "urn:kairos:problem:csrf-token-missing",
  "urn:kairos:problem:csrf-token-invalid",
]);

const csrfMetadataSchema = z.strictObject({
  token: z.string().check(z.minLength(1)),
  cookieName: z.literal(CSRF_COOKIE_NAME),
  headerName: z.literal(CSRF_HEADER_NAME),
});

let csrfInitialization: Promise<string> | undefined;
let csrfToken: string | undefined;

const pushPayloadSchema = z.strictObject({
  version: z.literal(1),
  eventId: z.uuid(),
  trackingReference: z.uuid(),
  status: z.enum(["IN_PREPARATION", "READY", "COMPLETED", "CANCELED"]),
  transitionedAt: z.iso.datetime({ offset: true }),
  orderUrl: z.string().check(z.startsWith("/orders/")),
});

const serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,
  precacheOptions: {
    cleanupOutdatedCaches: true,
  },
  skipWaiting: false,
  clientsClaim: false,
  navigationPreload: false,
  runtimeCaching: [],
});

serwist.registerCapture(
  ({ url }) =>
    url.origin === apiOrigin &&
    url.pathname.startsWith("/api/tracked-orders/v1/"),
  new NetworkOnly(),
  "GET",
);
serwist.registerRoute(new NavigationRoute(new NetworkOnly()));
serwist.setCatchHandler(async ({ request }) => {
  if (request.mode === "navigate") {
    return (await serwist.matchPrecache("/~offline")) ?? Response.error();
  }

  return Response.error();
});
serwist.addEventListeners();

self.addEventListener("push", (event) => {
  event.waitUntil(handlePush(event));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const orderUrl =
    typeof event.notification.data?.orderUrl === "string"
      ? event.notification.data.orderUrl
      : "/";

  event.waitUntil(openOrFocus(orderUrl));
});

self.addEventListener("pushsubscriptionchange", (event) => {
  const changeEvent = event as PushSubscriptionChangeEvent;

  changeEvent.waitUntil(replaceChangedSubscription(changeEvent));
});

async function handlePush(event: PushEvent): Promise<void> {
  const metadata = await readNotificationMetadata();

  if (metadata.notificationsEnabled !== true) {
    return;
  }
  let candidate: unknown;

  try {
    candidate = event.data?.json();
  } catch {
    candidate = null;
  }
  const result = z.safeParse(pushPayloadSchema, candidate);

  if (!result.success) {
    await self.registration.showNotification("Kairos", {
      body: "Your order status changed. Open Kairos for the latest status.",
      tag: "kairos-generic-order-update",
      data: { orderUrl: "/" },
    });

    return;
  }
  const payload = result.data;
  let transition;

  try {
    transition = await applyPushTransition({
      eventId: payload.eventId,
      trackingReference: payload.trackingReference,
      status: payload.status,
      transitionedAt: payload.transitionedAt,
    });
  } catch {
    await self.registration.showNotification("Kairos", {
      body: "Your order status changed. Open Kairos for the latest status.",
      tag: "kairos-generic-order-update",
      data: { orderUrl: "/" },
    });

    return;
  }

  await updateApplicationBadge(transition.activeOrderCount);
  if (!transition.shouldDisplay) {
    return;
  }
  await self.registration.showNotification("Kairos", {
    body: notificationBody(payload.status),
    tag: `kairos-order-${payload.trackingReference}`,
    data: {
      eventId: payload.eventId,
      orderUrl: payload.orderUrl,
    },
  });
  const clients = await self.clients.matchAll({
    includeUncontrolled: true,
    type: "window",
  });

  clients.forEach((client) => {
    client.postMessage({
      type: "KAIROS_ORDER_TRANSITION",
      eventId: payload.eventId,
      trackingReference: payload.trackingReference,
      status: payload.status,
      transitionedAt: payload.transitionedAt,
    });
  });
}

async function openOrFocus(orderUrl: string): Promise<void> {
  const target = new URL(orderUrl, self.location.origin);
  const clients = await self.clients.matchAll({
    includeUncontrolled: true,
    type: "window",
  });
  const matching = clients.find((client) => {
    const current = new URL(client.url);

    return (
      current.origin === target.origin && current.pathname === target.pathname
    );
  });

  if (matching && "focus" in matching) {
    await matching.focus();

    return;
  }
  await self.clients.openWindow(target.href);
}

async function replaceChangedSubscription(
  event: PushSubscriptionChangeEvent,
): Promise<void> {
  const previous = event.oldSubscription;
  const current =
    event.newSubscription ??
    (await self.registration.pushManager.getSubscription());

  if (!previous || !current) {
    return;
  }
  const metadata = await readNotificationMetadata();
  const previousSerialized = serializeInWorker(previous);
  const currentSerialized = serializeInWorker(current);
  const pending = {
    previous: previousSerialized,
    current: currentSerialized,
  };

  await updateNotificationMetadata({
    pendingSubscriptionReplacement: pending,
  });
  try {
    const body = JSON.stringify({
      previousSubscription: previousSerialized,
      currentSubscription: currentSerialized,
      trackingReferences: metadata.enrolledTrackingReferences ?? [],
    });
    let response = await sendSubscriptionReplacement(
      body,
      await getCsrfToken(),
    );

    if (await isCsrfFailure(response)) {
      resetCsrfToken();
      response = await sendSubscriptionReplacement(body, await getCsrfToken());
    }
    if (!response.ok) {
      return;
    }
    await updateNotificationMetadata({
      pendingSubscriptionReplacement: undefined,
      registeredEndpoint: current.endpoint,
    });
  } catch {
    // The next application start performs the idempotent reconciliation.
  }
}

async function getCsrfToken(): Promise<string> {
  if (csrfToken) {
    return csrfToken;
  }
  if (!csrfInitialization) {
    csrfInitialization = bootstrapCsrfToken()
      .then((token) => {
        csrfToken = token;

        return token;
      })
      .finally(() => {
        csrfInitialization = undefined;
      });
  }

  return csrfInitialization;
}

async function bootstrapCsrfToken(): Promise<string> {
  const response = await fetch(apiUrl("/api/auth/v1/csrf"), {
    credentials: "include",
    headers: { Accept: "application/json" },
  });

  if (!response.ok) {
    throw new Error(`CSRF bootstrap returned ${response.status}.`);
  }
  const result = z.safeParse(csrfMetadataSchema, await response.json());

  if (!result.success) {
    throw new Error("CSRF bootstrap returned an invalid response.");
  }

  return result.data.token;
}

function resetCsrfToken(): void {
  csrfToken = undefined;
}

function sendSubscriptionReplacement(
  body: string,
  token: string,
): Promise<Response> {
  return fetch(
    apiUrl("/api/customer-notifications/v1/subscription-replacement"),
    {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        [CSRF_HEADER_NAME]: token,
      },
      body,
    },
  );
}

async function isCsrfFailure(response: Response): Promise<boolean> {
  if (response.status !== 403) {
    return false;
  }
  try {
    const problem = (await response.json()) as { type?: unknown };

    return (
      typeof problem.type === "string" && CSRF_PROBLEM_TYPES.has(problem.type)
    );
  } catch {
    return false;
  }
}

function serializeInWorker(
  subscription: PushSubscription,
): SerializedPushSubscription {
  const p256dh = subscription.getKey("p256dh");
  const auth = subscription.getKey("auth");

  if (!p256dh || !auth) {
    throw new Error("The browser returned an incomplete Push subscription.");
  }

  return {
    endpoint: subscription.endpoint,
    expirationTime:
      subscription.expirationTime === null
        ? null
        : new Date(subscription.expirationTime).toISOString(),
    keys: {
      p256dh: toBase64Url(p256dh),
      auth: toBase64Url(auth),
    },
  };
}

function toBase64Url(value: ArrayBuffer): string {
  const bytes = new Uint8Array(value);
  let binary = "";

  for (let index = 0; index < bytes.length; index++) {
    binary += String.fromCharCode(bytes[index]);
  }

  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function notificationBody(status: OrderStatus): string {
  switch (status) {
    case "READY":
      return "Your order is ready for pickup";
    case "CANCELED":
      return "Your order was canceled";
    case "COMPLETED":
      return "Your order has been completed";
    case "IN_PREPARATION":
      return "Your order is in preparation";
  }
}

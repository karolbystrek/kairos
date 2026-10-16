/// <reference lib="webworker" />

import type { PrecacheEntry, SerwistGlobalConfig } from "serwist";

import { NavigationRoute, NetworkOnly, Serwist } from "serwist";
import * as z from "zod/mini";

import { apiOrigin } from "@/src/api/api-url";
import {
  replacePushSubscription,
  serializePushSubscription,
} from "@/src/api/customer-notifications";
import { orderStatusSchema, type OrderStatus } from "@/src/orders/order-status";
import { updateApplicationBadge } from "@/src/pwa/badge";
import {
  applyPushTransition,
  readNotificationMetadata,
  updateNotificationMetadata,
} from "@/src/pwa/storage";

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }

  // Supported by browsers but missing from TypeScript's DOM library.
  interface NotificationOptions {
    renotify?: boolean;
  }
}

declare const self: ServiceWorkerGlobalScope;

type PushSubscriptionChangeEvent = ExtendableEvent & {
  newSubscription?: PushSubscription | null;
  oldSubscription?: PushSubscription | null;
};

const pushPayloadSchema = z.strictObject({
  version: z.literal(1),
  eventId: z.uuid(),
  trackingReference: z.uuid(),
  status: orderStatusSchema,
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
  const payload = result.success ? result.data : null;
  const transition = payload
    ? await applyPushTransition({
        eventId: payload.eventId,
        trackingReference: payload.trackingReference,
        status: payload.status,
        transitionedAt: payload.transitionedAt,
      }).catch(() => null)
    : null;

  if (!payload || !transition) {
    await self.registration.showNotification("Kairos", {
      body: "Status zamówienia się zmienił. Otwórz Kairos, aby sprawdzić aktualny status.",
      tag: "kairos-generic-order-update",
      renotify: true,
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
    // Without renotify a later status silently replaces the earlier one.
    renotify: true,
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

  if (target.origin !== self.location.origin) return;
  const clients = await self.clients.matchAll({
    includeUncontrolled: true,
    type: "window",
  });
  const matching = clients.find((client) => {
    const current = new URL(client.url);

    return (
      current.origin === target.origin &&
      current.pathname === target.pathname &&
      current.search === target.search
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
  const pending = {
    previous: serializePushSubscription(previous),
    current: serializePushSubscription(current),
  };

  await updateNotificationMetadata({
    pendingSubscriptionReplacement: pending,
  });
  try {
    await replacePushSubscription(
      pending.previous,
      pending.current,
      metadata.enrolledTrackingReferences ?? [],
    );
    await updateNotificationMetadata({
      pendingSubscriptionReplacement: undefined,
      registeredEndpoint: current.endpoint,
    });
  } catch {
    // The next application start performs the idempotent reconciliation.
  }
}

function notificationBody(status: OrderStatus): string {
  switch (status) {
    case "READY":
      return "Twoje zamówienie jest gotowe do odbioru";
    case "CANCELED":
      return "Twoje zamówienie zostało anulowane";
    case "COMPLETED":
      return "Twoje zamówienie zostało zrealizowane";
    case "IN_PREPARATION":
      return "Twoje zamówienie jest w przygotowaniu";
  }
}

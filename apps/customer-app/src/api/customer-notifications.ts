import type { SerializedPushSubscription } from "@/src/pwa/storage";

import { z } from "zod";

const CSRF_COOKIE_NAME = "__Host-XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

const notificationConfigurationSchema = z
  .object({
    applicationServerKey: z.string().min(1),
  })
  .strict();

const notificationProblemSchema = z
  .object({
    code: z.string().optional(),
    detail: z.string().optional(),
    trackingReference: z.string().optional(),
  })
  .passthrough();

export class NotificationApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code?: string,
    public readonly trackingReference?: string,
  ) {
    super(`The customer notification API returned ${status}.`);
  }
}

export function serializePushSubscription(
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

export async function getNotificationConfiguration(): Promise<{
  applicationServerKey: string;
}> {
  const response = await fetch("/api/customer-notifications/v1/configuration");

  if (!response.ok) {
    throw await notificationApiError(response);
  }

  return notificationConfigurationSchema.parse(await response.json());
}

export async function reconcilePushSubscription(
  subscription: SerializedPushSubscription,
  trackingReferences: string[],
): Promise<string> {
  const csrfToken = await getCsrfToken();

  await notificationMutation(
    "/api/customer-notifications/v1/subscription",
    "PUT",
    {
      subscription,
      trackingReferences,
    },
    csrfToken,
  );

  return csrfToken;
}

export async function replacePushSubscription(
  previousSubscription: SerializedPushSubscription,
  currentSubscription: SerializedPushSubscription,
  trackingReferences: string[],
  csrfToken?: string,
): Promise<string> {
  const token = csrfToken ?? (await getCsrfToken());

  await notificationMutation(
    "/api/customer-notifications/v1/subscription-replacement",
    "POST",
    {
      previousSubscription,
      currentSubscription,
      trackingReferences,
    },
    token,
  );

  return token;
}

export async function disablePushSubscription(
  subscription: SerializedPushSubscription,
): Promise<void> {
  await notificationMutation(
    "/api/customer-notifications/v1/subscription",
    "DELETE",
    subscription,
    await getCsrfToken(),
  );
}

export async function removePushEnrollments(
  subscription: SerializedPushSubscription,
  trackingReferences: string[],
): Promise<void> {
  if (trackingReferences.length === 0) {
    return;
  }

  await notificationMutation(
    "/api/customer-notifications/v1/enrollments",
    "DELETE",
    {
      subscription,
      trackingReferences,
    },
    await getCsrfToken(),
  );
}

async function getCsrfToken(): Promise<string> {
  const response = await fetch("/api/auth/v1/csrf");

  if (!response.ok) {
    throw new NotificationApiError(response.status);
  }
  const token = readCookie(CSRF_COOKIE_NAME);

  if (!token) {
    throw new Error("The CSRF token cookie is unavailable.");
  }

  return token;
}

async function notificationMutation(
  url: string,
  method: "DELETE" | "POST" | "PUT",
  body: unknown,
  csrfToken: string,
): Promise<void> {
  const response = await fetch(url, {
    method,
    headers: {
      "Content-Type": "application/json",
      [CSRF_HEADER_NAME]: csrfToken,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw await notificationApiError(response);
  }
}

async function notificationApiError(
  response: Response,
): Promise<NotificationApiError> {
  try {
    const result = notificationProblemSchema.safeParse(await response.json());

    return new NotificationApiError(
      response.status,
      result.success ? result.data.code : undefined,
      result.success ? result.data.trackingReference : undefined,
    );
  } catch {
    return new NotificationApiError(response.status);
  }
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const prefix = `${name}=`;
  const value = document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith(prefix))
    ?.slice(prefix.length);

  return value ? decodeURIComponent(value) : null;
}

export function fromBase64Url(value: string): Uint8Array {
  const padding = "=".repeat((4 - (value.length % 4)) % 4);
  const binary = atob(
    (value + padding).replaceAll("-", "+").replaceAll("_", "/"),
  );
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index);
  }

  return bytes;
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

import type { SerializedPushSubscription } from "@/src/pwa/storage";

import { z } from "zod";

import { apiUrl } from "@/src/api/api-url";

const CSRF_COOKIE_NAME = "__Host-XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CSRF_PROBLEM_TYPES = new Set([
  "urn:kairos:problem:csrf-token-missing",
  "urn:kairos:problem:csrf-token-invalid",
]);

const csrfMetadataSchema = z
  .object({
    token: z.string().min(1),
    cookieName: z.literal(CSRF_COOKIE_NAME),
    headerName: z.literal(CSRF_HEADER_NAME),
  })
  .strict();

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
    type: z.string().optional(),
  })
  .passthrough();

let csrfInitialization: Promise<string> | undefined;
let csrfToken: string | undefined;

export class NotificationApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code?: string,
    public readonly trackingReference?: string,
    public readonly type?: string,
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
  const response = await fetch(
    apiUrl("/api/customer-notifications/v1/configuration"),
  );

  if (!response.ok) {
    throw await notificationApiError(response);
  }

  return notificationConfigurationSchema.parse(await response.json());
}

export async function reconcilePushSubscription(
  subscription: SerializedPushSubscription,
  trackingReferences: string[],
): Promise<void> {
  await notificationMutation(
    "/api/customer-notifications/v1/subscription",
    "PUT",
    {
      subscription,
      trackingReferences,
    },
  );
}

export async function replacePushSubscription(
  previousSubscription: SerializedPushSubscription,
  currentSubscription: SerializedPushSubscription,
  trackingReferences: string[],
): Promise<void> {
  await notificationMutation(
    "/api/customer-notifications/v1/subscription-replacement",
    "POST",
    {
      previousSubscription,
      currentSubscription,
      trackingReferences,
    },
  );
}

export async function disablePushSubscription(
  subscription: SerializedPushSubscription,
): Promise<void> {
  await notificationMutation(
    "/api/customer-notifications/v1/subscription",
    "DELETE",
    subscription,
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
  );
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
    throw await notificationApiError(response);
  }

  return csrfMetadataSchema.parse(await response.json()).token;
}

function resetCsrfToken(): void {
  csrfToken = undefined;
}

async function notificationMutation(
  url: string,
  method: "DELETE" | "POST" | "PUT",
  body: unknown,
): Promise<void> {
  let response = await sendNotificationMutation(
    url,
    method,
    body,
    await getCsrfToken(),
  );

  if (response.status === 403) {
    const error = await notificationApiError(response);

    if (!error.type || !CSRF_PROBLEM_TYPES.has(error.type)) {
      throw error;
    }
    resetCsrfToken();
    response = await sendNotificationMutation(
      url,
      method,
      body,
      await getCsrfToken(),
    );
  }

  if (!response.ok) {
    throw await notificationApiError(response);
  }
}

function sendNotificationMutation(
  url: string,
  method: "DELETE" | "POST" | "PUT",
  body: unknown,
  token: string,
): Promise<Response> {
  return fetch(apiUrl(url), {
    method,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      [CSRF_HEADER_NAME]: token,
    },
    body: JSON.stringify(body),
  });
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
      result.success ? result.data.type : undefined,
    );
  } catch {
    return new NotificationApiError(response.status);
  }
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

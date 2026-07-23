import { z } from "zod";

import {
  notifyAuthenticationRequired,
  withAuthCookieLock,
} from "./auth-coordination";

const csrfCookieName = "__Host-XSRF-TOKEN";
const csrfHeaderName = "X-XSRF-TOKEN";
const csrfProblemTypes = new Set([
  "urn:kairos:problem:csrf-token-missing",
  "urn:kairos:problem:csrf-token-invalid",
]);

const csrfMetadataSchema = z.object({
  cookieName: z.literal(csrfCookieName),
  headerName: z.literal(csrfHeaderName),
});

const problemDetailsSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().int().optional(),
  detail: z.string().optional(),
});

type ApiFetchOptions = {
  retryUnauthorized?: boolean;
};

type ProblemDetails = z.infer<typeof problemDetailsSchema>;

let csrfInitialization: Promise<void> | undefined;
let isCsrfInitialized = false;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem?: ProblemDetails,
  ) {
    super(problem?.detail ?? defaultErrorMessage(status));
    this.name = "ApiError";
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    let problem: ProblemDetails | undefined;

    try {
      const result = problemDetailsSchema.safeParse(await response.json());

      if (result.success) problem = result.data;
    } catch {
      // Some valid error responses, including proxy failures, have no JSON body.
    }

    return new ApiError(response.status, problem);
  }
}

function defaultErrorMessage(status: number): string {
  switch (status) {
    case 400:
      return "The request was not valid.";
    case 401:
      return "Your session has expired. Sign in again to continue.";
    case 403:
      return "You are not allowed to perform this action.";
    case 404:
      return "The requested resource was not found.";
    case 409:
      return "The request conflicts with the current state.";
    case 429:
      return "Too many requests. Please wait and try again.";
    default:
      return `The API returned ${status}.`;
  }
}

function isUnsafeRequest(init?: RequestInit): boolean {
  const method = (init?.method ?? "GET").toUpperCase();

  return !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method);
}

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;

  const prefix = `${encodeURIComponent(name)}=`;
  const cookie = document.cookie
    .split("; ")
    .find((value) => value.startsWith(prefix));

  if (!cookie) return undefined;

  const value = cookie.slice(prefix.length);

  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

async function fetchCsrfMetadata(): Promise<void> {
  const response = await fetch("/api/auth/csrf", {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });

  if (!response.ok) throw await ApiError.fromResponse(response);

  csrfMetadataSchema.parse(await response.json());

  if (!readCookie(csrfCookieName)) {
    throw new Error("The API did not issue the required CSRF cookie.");
  }
}

export function initializeCsrf(): Promise<void> {
  if (isCsrfInitialized && readCookie(csrfCookieName)) {
    return Promise.resolve();
  }

  if (!csrfInitialization) {
    csrfInitialization = fetchCsrfMetadata()
      .then(() => {
        isCsrfInitialized = true;
      })
      .finally(() => {
        csrfInitialization = undefined;
      });
  }

  return csrfInitialization;
}

function resetCsrfInitialization() {
  isCsrfInitialized = false;
}

async function send(url: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers);

  if (!headers.has("Accept")) headers.set("Accept", "application/json");

  if (isUnsafeRequest(init)) {
    await initializeCsrf();

    const token = readCookie(csrfCookieName);

    if (!token) throw new Error("The CSRF token is unavailable.");

    headers.set(csrfHeaderName, token);
  }

  return fetch(url, {
    ...init,
    credentials: "same-origin",
    headers,
  });
}

async function sendWithCsrfRecovery(
  url: string,
  init?: RequestInit,
): Promise<Response> {
  const response = await send(url, init);

  if (response.status !== 403 || !isUnsafeRequest(init)) return response;

  const error = await ApiError.fromResponse(response);

  if (!error.problem?.type || !csrfProblemTypes.has(error.problem.type)) {
    throw error;
  }

  resetCsrfInitialization();
  await initializeCsrf();

  return send(url, init);
}

async function recoverSessionAndRetry(
  url: string,
  init: RequestInit | undefined,
  isAuthCookieLockHeld: boolean,
): Promise<Response> {
  const recoverAndRetry = async () => {
    const currentSession = await sendWithCsrfRecovery("/api/auth/me");

    if (!currentSession.ok && currentSession.status !== 401) {
      throw await ApiError.fromResponse(currentSession);
    }

    if (currentSession.status === 401) {
      const refresh = await sendWithCsrfRecovery("/api/auth/refresh", {
        method: "POST",
      });

      if (!refresh.ok) return refresh;
    }

    return sendWithCsrfRecovery(url, init);
  };

  if (isAuthCookieLockHeld) {
    return recoverAndRetry();
  }

  return withAuthCookieLock(recoverAndRetry);
}

async function apiFetchInternal(
  url: string,
  init: RequestInit | undefined,
  options: ApiFetchOptions | undefined,
  isAuthCookieLockHeld: boolean,
): Promise<Response> {
  let response = await sendWithCsrfRecovery(url, init);

  if (response.status === 401 && options?.retryUnauthorized !== false) {
    response = await recoverSessionAndRetry(url, init, isAuthCookieLockHeld);
  }

  if (!response.ok) {
    const error = await ApiError.fromResponse(response);

    if (error.status === 401 && options?.retryUnauthorized !== false) {
      notifyAuthenticationRequired();
    }

    throw error;
  }

  return response;
}

export function apiFetch(
  url: string,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<Response> {
  return apiFetchInternal(url, init, options, false);
}

export function apiFetchWhileAuthLocked(
  url: string,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<Response> {
  return apiFetchInternal(url, init, options, true);
}

export async function request<T>(
  url: string,
  schema: z.ZodType<T>,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<T> {
  const response = await apiFetch(url, init, options);

  return schema.parse(await response.json());
}

export async function requestWhileAuthLocked<T>(
  url: string,
  schema: z.ZodType<T>,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<T> {
  const response = await apiFetchWhileAuthLocked(url, init, options);

  return schema.parse(await response.json());
}

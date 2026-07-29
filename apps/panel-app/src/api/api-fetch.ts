import { z } from "zod";

import {
  notifyAuthenticationRequired,
  withAuthCookieLock,
} from "./auth-coordination";

import { apiBaseUrl } from "@/src/config/public-environment";

const csrfCookieName = "__Host-XSRF-TOKEN";
const csrfHeaderName = "X-XSRF-TOKEN";
const csrfProblemTypes = new Set([
  "urn:kairos:problem:csrf-token-missing",
  "urn:kairos:problem:csrf-token-invalid",
]);

const csrfMetadataSchema = z.object({
  token: z.string().min(1),
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

let csrfInitialization: Promise<string> | undefined;
let csrfToken: string | undefined;

function apiUrl(path: string): string {
  return new URL(path, apiBaseUrl).toString();
}

function logTechnicalError(message: string, error: unknown): void {
  // Technical details belong in diagnostics, never in user-facing messages.
  // eslint-disable-next-line no-console
  console.error(message, error);
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem?: ProblemDetails,
  ) {
    super(defaultErrorMessage(status));
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
      return "Check the submitted values and try again.";
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
      return "The request could not be completed. Check your connection and try again.";
  }
}

function isUnsafeRequest(init?: RequestInit): boolean {
  const method = (init?.method ?? "GET").toUpperCase();

  return !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method);
}

async function fetchCsrfMetadata(): Promise<string> {
  try {
    const response = await fetch(apiUrl("/api/auth/v1/csrf"), {
      credentials: "include",
      headers: { Accept: "application/json" },
    });

    if (!response.ok) throw await ApiError.fromResponse(response);

    return csrfMetadataSchema.parse(await response.json()).token;
  } catch (error) {
    logTechnicalError("Panel security setup failed.", error);

    if (error instanceof ApiError) throw error;

    throw new Error("Security setup could not be completed.", {
      cause: error,
    });
  }
}

export function initializeCsrf(): Promise<string> {
  if (csrfToken) {
    return Promise.resolve(csrfToken);
  }

  if (!csrfInitialization) {
    csrfInitialization = fetchCsrfMetadata()
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

export async function refreshCsrf(): Promise<string> {
  resetCsrf();

  return initializeCsrf();
}

function resetCsrf(): void {
  csrfToken = undefined;
}

async function send(url: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers);

  if (!headers.has("Accept")) headers.set("Accept", "application/json");

  if (isUnsafeRequest(init)) {
    const token = await initializeCsrf();

    headers.set(csrfHeaderName, token);
  }

  return fetch(apiUrl(url), {
    ...init,
    credentials: "include",
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

  resetCsrf();
  await initializeCsrf();

  return send(url, init);
}

async function recoverSessionAndRetry(
  url: string,
  init: RequestInit | undefined,
  isAuthCookieLockHeld: boolean,
): Promise<Response> {
  const recoverAndRetry = async () => {
    const currentSession = await sendWithCsrfRecovery("/api/auth/v1/me");

    if (!currentSession.ok && currentSession.status !== 401) {
      throw await ApiError.fromResponse(currentSession);
    }

    if (currentSession.status === 401) {
      const refresh = await sendWithCsrfRecovery("/api/auth/v1/refresh", {
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

export async function apiFetch(
  url: string,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<Response> {
  try {
    return await apiFetchInternal(url, init, options, false);
  } catch (error) {
    logTechnicalError("Panel API request failed.", error);
    throw error;
  }
}

export async function apiFetchWhileAuthLocked(
  url: string,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<Response> {
  try {
    return await apiFetchInternal(url, init, options, true);
  } catch (error) {
    logTechnicalError("Panel API request failed.", error);
    throw error;
  }
}

async function parseResponse<T>(
  response: Response,
  schema: z.ZodType<T>,
): Promise<T> {
  try {
    return schema.parse(await response.json());
  } catch (error) {
    logTechnicalError("Panel API response validation failed.", error);
    throw new Error("The server response could not be read.", {
      cause: error,
    });
  }
}

export async function request<T>(
  url: string,
  schema: z.ZodType<T>,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<T> {
  const response = await apiFetch(url, init, options);

  return parseResponse(response, schema);
}

export async function requestWhileAuthLocked<T>(
  url: string,
  schema: z.ZodType<T>,
  init?: RequestInit,
  options?: ApiFetchOptions,
): Promise<T> {
  const response = await apiFetchWhileAuthLocked(url, init, options);

  return parseResponse(response, schema);
}

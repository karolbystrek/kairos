import { z } from "zod";

import { notifyAuthenticationRequired } from "./auth-state";

import { apiBaseUrl } from "@/src/config/public-environment";

const csrfHeaderName = "X-XSRF-TOKEN";
// Keep in sync with apps/customer-app/src/api/customer-notifications.ts.
const csrfProblemTypes = new Set([
  "urn:kairos:problem:csrf-token-missing",
  "urn:kairos:problem:csrf-token-invalid",
]);

const csrfMetadataSchema = z.object({
  token: z.string().min(1),
});

const problemDetailsSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().int().optional(),
  detail: z.string().optional(),
  fieldErrors: z.record(z.string(), z.string()).optional(),
});

type ApiFetchOptions = {
  notifyUnauthorized?: boolean;
};

type ProblemDetails = z.infer<typeof problemDetailsSchema>;

let csrfInitialization: Promise<string> | undefined;
let csrfToken: string | undefined;

function apiUrl(path: string): string {
  return new URL(path, apiBaseUrl).toString();
}

// A stalled request aborts after 10 s and fails like any other network error.
function requestSignal(signal?: AbortSignal | null): AbortSignal {
  const timeout = AbortSignal.timeout(10_000);

  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

function isTimeout(error: unknown): boolean {
  return error instanceof DOMException && error.name === "TimeoutError";
}

function logTechnicalError(message: string, error: unknown): void {
  // 4xx answers (signed out, validation, conflict, rate limit) are expected
  // outcomes that callers present to the user, not technical failures.
  if (error instanceof ApiError && error.status < 500) return;
  // Timeouts are connectivity problems, not bugs.
  if (isTimeout(error) || (error instanceof Error && isTimeout(error.cause)))
    return;

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
      return "Sprawdź wprowadzone dane i spróbuj ponownie.";
    case 401:
      return "Sesja wygasła. Zaloguj się ponownie, aby kontynuować.";
    case 403:
      return "Nie masz uprawnień do wykonania tej czynności.";
    case 404:
      return "Nie znaleziono żądanych danych.";
    case 409:
      return "Nie można wykonać tej czynności w obecnym stanie.";
    case 429:
      return "Zbyt wiele żądań. Poczekaj i spróbuj ponownie.";
    default:
      return "Nie udało się wykonać żądania. Sprawdź połączenie i spróbuj ponownie.";
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
      signal: requestSignal(),
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

export function resetCsrf(): void {
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
    signal: requestSignal(init?.signal),
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

async function apiFetchInternal(
  url: string,
  init: RequestInit | undefined,
  options: ApiFetchOptions | undefined,
): Promise<Response> {
  const response = await sendWithCsrfRecovery(url, init);

  if (!response.ok) {
    const error = await ApiError.fromResponse(response);

    if (error.status === 401 && options?.notifyUnauthorized !== false) {
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
    return await apiFetchInternal(url, init, options);
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

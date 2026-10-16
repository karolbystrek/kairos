const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

if (!apiBaseUrl) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured.");
}

export const apiOrigin = new URL(apiBaseUrl).origin;

export function apiUrl(path: string): string {
  return new URL(path, apiOrigin).toString();
}

/** Aborts a REST request after 10 s so a stalled network reaches the error and stored-snapshot fallbacks. */
export function requestSignal(signal?: AbortSignal | null): AbortSignal {
  const timeout = AbortSignal.timeout(10_000);

  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

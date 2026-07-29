const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

if (!apiBaseUrl) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured.");
}

export const apiOrigin = new URL(apiBaseUrl).origin;

export function apiUrl(path: string): string {
  return new URL(path, apiOrigin).toString();
}

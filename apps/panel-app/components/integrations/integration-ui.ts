import { ZodError } from "zod";

import { ApiError } from "@/src/api/api-fetch";

export function getIntegrationErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "The submitted values are not valid.";
  }

  if (error instanceof ApiError) return error.message;

  return "The integration request could not be completed. Check your connection and try again.";
}

export function shouldRetryIntegrationRequest(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

export function formatIntegrationDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

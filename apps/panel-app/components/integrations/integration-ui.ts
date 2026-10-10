import { ZodError } from "zod";

import { ApiError } from "@/src/api/api-fetch";

export function getIntegrationErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Wprowadzone dane są nieprawidłowe.";
  }

  if (error instanceof ApiError) return error.message;

  return "Nie udało się wykonać operacji na integracji. Sprawdź połączenie i spróbuj ponownie.";
}

export function shouldRetryIntegrationRequest(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

export function formatIntegrationDateTime(value: string): string {
  return new Intl.DateTimeFormat("pl-PL", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

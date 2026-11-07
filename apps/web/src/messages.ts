import { ZodError } from "zod";

import { ApiError } from "@/src/api/api-fetch";

// Validation and API problems carry user-facing text; anything else gets the caller's fallback.
export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ZodError) return error.issues[0]?.message ?? fallback;
  if (error instanceof ApiError) return error.message;

  return fallback;
}

const dateTimeFormat = new Intl.DateTimeFormat("pl-PL", {
  dateStyle: "medium",
  timeStyle: "short",
});

export function formatDateTime(value: string): string {
  return dateTimeFormat.format(new Date(value));
}

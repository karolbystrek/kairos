import { errorMessage } from "@/src/messages";

export function getIntegrationErrorMessage(error: unknown): string {
  return errorMessage(
    error,
    "Nie udało się wykonać operacji na integracji. Sprawdź połączenie i spróbuj ponownie.",
  );
}

"use client";

import { RefreshCw } from "lucide-react";
import { Alert, Button } from "@heroui/react";
import { useEffect } from "react";

export default function Error({
  error,
  reset,
}: {
  error: Error;
  reset: () => void;
}) {
  useEffect(() => {
    // Log the error to an error reporting service
    /* eslint-disable no-console */
    console.error(error);
  }, [error]);

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <Alert className="max-w-lg" status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Nie udało się wczytać panelu obsługi</Alert.Title>
          <Alert.Description>
            Nie wysłano żadnej operacji na zamówieniu. Spróbuj ponownie wczytać
            panel.
          </Alert.Description>
          <Button className="mt-4" size="sm" variant="danger" onPress={reset}>
            <RefreshCw aria-hidden="true" size={18} /> Spróbuj ponownie
          </Button>
        </Alert.Content>
      </Alert>
    </div>
  );
}

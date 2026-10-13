"use client";

import { Alert, Button } from "@heroui/react";
import { ArrowsClockwiseIcon } from "@phosphor-icons/react";
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
    <div className="flex flex-1 items-center justify-center">
      <Alert className="max-w-lg" status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Nie udało się otworzyć tego widoku</Alert.Title>
          <Button className="mt-4" size="sm" variant="danger" onPress={reset}>
            <ArrowsClockwiseIcon aria-hidden="true" size={18} /> Spróbuj
            ponownie
          </Button>
        </Alert.Content>
      </Alert>
    </div>
  );
}

"use client";

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
    <div className="flex min-h-[60vh] items-center justify-center">
      <Alert className="max-w-lg" status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Kairos could not open this view</Alert.Title>
          <Alert.Description>
            Your saved orders are unchanged. Try loading the view again.
          </Alert.Description>
          <Button className="mt-4" size="sm" variant="danger" onPress={reset}>
            Try again
          </Button>
        </Alert.Content>
      </Alert>
    </div>
  );
}

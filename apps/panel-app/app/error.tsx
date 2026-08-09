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
    <div className="flex min-h-[70vh] items-center justify-center">
      <Alert className="max-w-lg" status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>The staff workspace could not load</Alert.Title>
          <Alert.Description>
            No order action was submitted. Try loading the workspace again.
          </Alert.Description>
          <Button className="mt-4" size="sm" variant="danger" onPress={reset}>
            Try again
          </Button>
        </Alert.Content>
      </Alert>
    </div>
  );
}

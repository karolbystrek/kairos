"use client";

import { Alert, Link } from "@heroui/react";

export default function NotFound() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <Alert className="max-w-lg" status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Nie znaleziono strony</Alert.Title>
          <Alert.Description>
            Sprawdź adres lub wróć do strony głównej.
          </Alert.Description>
          <Link className="mt-4" href="/">
            Wróć do strony głównej
          </Link>
        </Alert.Content>
      </Alert>
    </div>
  );
}

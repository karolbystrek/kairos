"use client";

import { Alert, Link } from "@heroui/react";

import { paths } from "@/src/routes";

export function NotFoundNotice() {
  return (
    <div className="flex flex-1 items-center justify-center">
      <Alert className="max-w-lg" status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Nie znaleziono strony</Alert.Title>
          <Alert.Description>
            Sprawdź adres lub wróć do strony głównej.
          </Alert.Description>
          <Link className="mt-4" href={paths.home}>
            Wróć do strony głównej
          </Link>
        </Alert.Content>
      </Alert>
    </div>
  );
}

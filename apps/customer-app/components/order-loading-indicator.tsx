"use client";

import { Spinner } from "@heroui/react";

export function OrderLoadingIndicator({
  label = "Wczytywanie zamówienia",
}: {
  label?: string;
}) {
  return (
    <div
      className="flex flex-1 items-center justify-center py-12 sm:py-16"
      role="status"
    >
      <Spinner aria-hidden="true" size="sm" />
      <span className="sr-only">{label}</span>
    </div>
  );
}

"use client";

import { Skeleton } from "@heroui/react";

export function OrderLoadingSkeleton({
  label = "Wczytywanie zamówienia",
}: {
  label?: string;
}) {
  return (
    <div
      className="flex flex-1 flex-col items-center justify-center py-12 text-center sm:py-16"
      role="status"
    >
      <span className="sr-only">{label}</span>
      <Skeleton
        animationType="pulse"
        aria-hidden="true"
        className="h-10 w-24 rounded-md"
      />
      <Skeleton
        animationType="pulse"
        aria-hidden="true"
        className="mt-4 h-[2em] w-full max-w-[14ch] rounded-lg status-title"
      />
    </div>
  );
}

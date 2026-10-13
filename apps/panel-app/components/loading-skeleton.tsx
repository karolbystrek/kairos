import type { ReactNode } from "react";

import { Skeleton } from "@heroui/react";

// Placeholders announce one label; the grey shapes stay out of the accessibility tree.
export function LoadingStatus({
  children,
  className,
  label,
}: {
  children: ReactNode;
  className?: string;
  label: string;
}) {
  return (
    <div className={className} role="status">
      <span className="sr-only">{label}</span>
      {children}
    </div>
  );
}

export function CardSkeleton({
  metadata = false,
  trailing = false,
}: {
  metadata?: boolean;
  trailing?: boolean;
}) {
  return (
    <div aria-hidden="true" className="panel-card px-3 py-2">
      <div className="panel-card-content h-full">
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <Skeleton animationType="pulse" className="h-6 w-2/3 rounded-md" />
          {metadata && (
            <Skeleton animationType="pulse" className="h-3 w-1/3 rounded-md" />
          )}
        </div>
        {trailing && (
          <Skeleton animationType="pulse" className="h-12 w-32 rounded-xl" />
        )}
      </div>
    </div>
  );
}

export function CardGridSkeleton({
  cards = 6,
  label,
  metadata,
}: {
  cards?: number;
  label: string;
  metadata?: boolean;
}) {
  return (
    <LoadingStatus className="entity-card-grid" label={label}>
      {Array.from({ length: cards }, (_, index) => (
        <CardSkeleton key={index} metadata={metadata} />
      ))}
    </LoadingStatus>
  );
}

export function RowListSkeleton({
  label,
  rows = 2,
  apiKeys = false,
}: {
  label: string;
  rows?: number;
  apiKeys?: boolean;
}) {
  return (
    <LoadingStatus className="border-t border-separator" label={label}>
      {Array.from({ length: rows }, (_, index) => (
        <div
          key={index}
          aria-hidden="true"
          className="flex flex-col gap-3 border-b border-separator py-4"
        >
          <div className="flex items-start justify-between gap-3">
            <Skeleton animationType="pulse" className="h-5 w-40 rounded-md" />
            <Skeleton animationType="pulse" className="h-4 w-16 rounded-md" />
          </div>
          {[0, 1].map((line) => (
            <div key={line} className="flex flex-col gap-1">
              <Skeleton animationType="pulse" className="h-4 w-28 rounded-md" />
              <Skeleton
                animationType="pulse"
                className="h-4 w-2/3 rounded-md"
              />
            </div>
          ))}
          <div className="flex flex-col gap-1">
            {[0, 1, 2].map((line) => (
              <Skeleton
                key={line}
                animationType="pulse"
                className="h-4 w-2/3 rounded-md"
              />
            ))}
            {!apiKeys && (
              <Skeleton
                animationType="pulse"
                className="h-4 w-1/2 rounded-md"
              />
            )}
          </div>
          <Skeleton
            animationType="pulse"
            className="h-[var(--control-regular)] w-24 rounded-md"
          />
        </div>
      ))}
    </LoadingStatus>
  );
}

// FormSelect's label is visually hidden, so only its trigger takes space.
export function FieldSkeleton({ className = "" }: { className?: string }) {
  return (
    <Skeleton
      animationType="pulse"
      aria-hidden="true"
      className={`h-[var(--control-regular)] rounded-[var(--radius-medium)] ${className}`}
    />
  );
}

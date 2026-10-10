import type { ReactNode } from "react";

import { Button } from "@heroui/react";

export function PanelCard({
  accessibilityLabel,
  isSelected = false,
  metadata,
  onPress,
  title,
  status,
  trailing,
}: {
  accessibilityLabel?: string;
  isSelected?: boolean;
  metadata?: ReactNode;
  onPress?: () => void;
  title: string;
  status?: "ENABLED" | "DISABLED" | "PENDING";
  trailing?: ReactNode;
}) {
  return (
    <article
      className="panel-card relative px-3 py-2"
      data-selected={isSelected || undefined}
      data-status={status}
    >
      {onPress && accessibilityLabel && (
        <Button
          aria-current={isSelected ? "true" : undefined}
          aria-label={accessibilityLabel}
          className="absolute inset-0 z-0 bg-transparent outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-inset"
          variant="tertiary"
          onPress={onPress}
        >
          <span className="sr-only">{accessibilityLabel}</span>
        </Button>
      )}
      <div
        className={`panel-card-content relative z-10 ${onPress ? "pointer-events-none" : ""}`}
      >
        <div className="min-w-0 flex-1">
          <p className="break-words text-lg font-semibold tracking-[-0.025em]">
            {title}
          </p>
          {metadata && <div className="mt-0.5 min-w-0 text-xs">{metadata}</div>}
        </div>
        {trailing && (
          <div className="panel-card-trailing flex max-w-full shrink-0 items-center justify-end gap-2">
            {trailing}
          </div>
        )}
      </div>
    </article>
  );
}

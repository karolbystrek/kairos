import type { ReactNode } from "react";

import { Button, Chip } from "@heroui/react";

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
  title: ReactNode;
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
          className="absolute inset-0 z-0 bg-transparent outline-none"
          variant="tertiary"
          onPress={onPress}
        >
          <span className="sr-only">{accessibilityLabel}</span>
        </Button>
      )}
      <div
        className={`panel-card-content relative z-10 ${onPress ? "pointer-events-none" : ""}`}
      >
        <div className="min-w-0 flex-[1_1_10rem]">
          <p className="break-words text-lg font-semibold tracking-[-0.025em]">
            {title}
          </p>
          {metadata && <div className="mt-1 min-w-0 text-sm">{metadata}</div>}
          {(status === "ENABLED" || status === "DISABLED") && (
            <Chip
              className="mt-2"
              color={status === "ENABLED" ? "success" : "default"}
              size="sm"
              variant="soft"
            >
              {status === "ENABLED" ? "Włączone" : "Wyłączone"}
            </Chip>
          )}
        </div>
        {trailing && (
          <div className="panel-card-trailing flex max-w-full flex-wrap items-center justify-end gap-2">
            {trailing}
          </div>
        )}
      </div>
    </article>
  );
}

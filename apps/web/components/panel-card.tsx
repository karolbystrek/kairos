import type { ReactNode } from "react";
import type { EntityStatus } from "@/components/status-chip";

import { Button } from "@heroui/react";

import { StatusChip } from "@/components/status-chip";

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
  status?: EntityStatus;
  trailing?: ReactNode;
}) {
  return (
    <article
      className="panel-card relative px-3 py-2"
      data-selected={isSelected || undefined}
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
          {status && <StatusChip className="mt-2" status={status} />}
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

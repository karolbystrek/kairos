import type { ReactNode } from "react";

import { Chip } from "@heroui/react";

export function PanelDetailHeader({
  kind,
  status,
  title,
  titleEditor,
  titleAction,
  trailingActions,
}: {
  kind: string;
  status?: { isEnabled: boolean; label: string };
  title: string;
  titleEditor?: ReactNode;
  titleAction?: ReactNode;
  trailingActions: ReactNode;
}) {
  return (
    <header className="min-w-0">
      <div className="flex min-w-0 flex-wrap items-center gap-3">
        <div className="min-w-0 flex-[1_1_14rem]">
          {titleEditor ?? (
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              <h2 className="break-words text-2xl font-semibold tracking-tight">
                {title}
              </h2>
              {titleAction}
              {status && (
                <Chip
                  className="shrink-0"
                  color={status.isEnabled ? "success" : "default"}
                  size="sm"
                  variant="soft"
                >
                  {status.label}
                </Chip>
              )}
            </div>
          )}
        </div>
        <div
          aria-label={`${kind} — opcje`}
          className="grid min-w-0 flex-[1_1_20rem] grid-cols-2 items-center gap-2 [&_.button]:w-full [&_.button]:h-auto [&_.button]:min-h-[var(--control-regular)] [&_.button]:whitespace-normal"
          role="group"
        >
          {trailingActions}
        </div>
      </div>
    </header>
  );
}

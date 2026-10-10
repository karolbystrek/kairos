import type { ReactNode } from "react";

export function PanelDetailHeader({
  eyebrow,
  title,
  titleEditor,
  titleAction,
  trailingActions,
}: {
  eyebrow: string;
  title: string;
  titleEditor?: ReactNode;
  titleAction?: ReactNode;
  trailingActions: ReactNode;
}) {
  return (
    <header className="min-w-0">
      <p className="text-xs font-medium uppercase tracking-[0.1em] secondary-text">
        {eyebrow}
      </p>
      <div className="mt-1 flex min-w-0 flex-wrap items-center gap-3">
        <div className="min-w-0 flex-[1_1_14rem]">
          {titleEditor ?? (
            <div className="flex min-w-0 items-center gap-2">
              <h2 className="truncate text-2xl font-semibold tracking-tight">
                {title}
              </h2>
              {titleAction}
            </div>
          )}
        </div>
        <div
          aria-label={`${eyebrow} — opcje`}
          className="grid min-w-0 flex-[1_1_20rem] grid-cols-2 items-center gap-2 [&_.button]:w-full [&_.button]:h-auto [&_.button]:min-h-[var(--control-regular)] [&_.button]:whitespace-normal"
          role="group"
        >
          {trailingActions}
        </div>
      </div>
    </header>
  );
}

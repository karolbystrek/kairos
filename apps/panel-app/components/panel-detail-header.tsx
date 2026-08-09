import type { ReactNode } from "react";

export function PanelDetailHeader({
  eyebrow,
  title,
  titleEditor,
  trailingActions,
}: {
  eyebrow: string;
  title: string;
  titleEditor?: ReactNode;
  trailingActions: ReactNode;
}) {
  return (
    <header className="min-w-0">
      <p className="text-xs font-medium uppercase tracking-[0.1em] secondary-text">
        {eyebrow}
      </p>
      <div className="mt-1 grid min-w-0 grid-cols-[minmax(0,1fr)_auto] items-center gap-3">
        <div className="min-w-0">
          {titleEditor ?? (
            <h2 className="truncate text-2xl font-semibold tracking-tight">
              {title}
            </h2>
          )}
        </div>
        <div
          aria-label={`${eyebrow} actions`}
          className="flex shrink-0 items-center justify-end gap-2"
          role="group"
        >
          {trailingActions}
        </div>
      </div>
    </header>
  );
}

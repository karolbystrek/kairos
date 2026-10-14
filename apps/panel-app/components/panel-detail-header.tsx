import type { EntityStatus } from "@/components/status-chip";

import { PanelPopup } from "@/components/panel-popup";
import { StatusChip } from "@/components/status-chip";

// Title and status share the top row with the popup's Close control.
export function PanelDetailHeader({
  status,
  title,
}: {
  status?: EntityStatus;
  title: string;
}) {
  return (
    <PanelPopup.Header className="flex-row flex-wrap items-center gap-x-3 gap-y-1">
      <PanelPopup.Heading className="min-w-0 break-words text-2xl font-semibold tracking-tight">
        {title}
      </PanelPopup.Heading>
      {/* h-8 matches the heading's text-2xl line height. */}
      {status && <StatusChip className="h-8 px-3 text-sm" status={status} />}
    </PanelPopup.Header>
  );
}

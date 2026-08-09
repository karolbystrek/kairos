"use client";

import { Button, Tooltip } from "@heroui/react";
import { House } from "lucide-react";

import { ThemeMenu } from "@/components/theme-menu";
import { NotificationControl } from "@/src/pwa/notification-control";

export function CustomerToolbar({
  className = "",
  onHome,
}: {
  className?: string;
  onHome?: () => void;
}) {
  return (
    <div className={`flex items-center justify-end gap-1 ${className}`}>
      {onHome && (
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label="Go to Your orders"
              className="rounded-md"
              variant="tertiary"
              onPress={onHome}
            >
              <House aria-hidden="true" size={20} />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content>Your orders</Tooltip.Content>
        </Tooltip>
      )}
      <NotificationControl />
      <ThemeMenu />
    </div>
  );
}

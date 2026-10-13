"use client";

import { ThemeToggle } from "@/components/theme-toggle";
import { NotificationControl } from "@/src/pwa/notification-control";

export function CustomerToolbar({ className = "" }: { className?: string }) {
  return (
    <div className={`flex items-center justify-end gap-1 ${className}`}>
      <NotificationControl />
      <ThemeToggle />
    </div>
  );
}

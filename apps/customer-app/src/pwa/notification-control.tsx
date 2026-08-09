"use client";

import { Alert, Button, Tooltip } from "@heroui/react";
import { Bell, BellOff, X } from "lucide-react";

import {
  type NotificationState,
  useCustomerNotifications,
} from "@/src/pwa/notification-provider";

export function NotificationIcon({ enabled }: { enabled: boolean }) {
  const Icon = enabled ? BellOff : Bell;

  return <Icon aria-hidden="true" size={20} />;
}

function notificationActionLabel(state: NotificationState): string {
  switch (state) {
    case "enabled":
      return "Disable notifications";
    case "blocked":
      return "Notifications are blocked";
    case "unsupported":
      return "Notifications are not supported";
    case "loading":
      return "Loading notification settings";
    default:
      return "Enable notifications";
  }
}

export function NotificationControl() {
  const { disable, dismissMessage, enable, message, state } =
    useCustomerNotifications();
  const isEnabled = state === "enabled";
  const isUnavailable = state === "loading" || state === "unsupported";
  const actionLabel = notificationActionLabel(state);

  return (
    <>
      <Tooltip delay={500}>
        <Tooltip.Trigger>
          <Button
            isIconOnly
            aria-label={actionLabel}
            aria-pressed={isEnabled}
            className="rounded-md"
            isDisabled={isUnavailable || state === "blocked"}
            variant={isEnabled ? "primary" : "tertiary"}
            onPress={() => {
              void (isEnabled ? disable() : enable());
            }}
          >
            <NotificationIcon enabled={isEnabled} />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content>{actionLabel}</Tooltip.Content>
      </Tooltip>
      {message && (
        <div className="notification-region fixed inset-x-0 z-50 px-5 sm:px-6">
          <div className="mx-auto max-w-[var(--content-customer)]">
            <Alert
              className="notification-banner"
              status={
                state === "blocked" || state === "error" ? "warning" : "default"
              }
            >
              <Alert.Indicator />
              <Alert.Content className="min-w-0">
                <Alert.Description>{message}</Alert.Description>
              </Alert.Content>
              <Tooltip delay={500}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    aria-label="Dismiss notification"
                    className="notification-dismiss rounded-md"
                    variant="tertiary"
                    onPress={dismissMessage}
                  >
                    <X aria-hidden="true" size={18} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Dismiss</Tooltip.Content>
              </Tooltip>
            </Alert>
          </div>
        </div>
      )}
    </>
  );
}

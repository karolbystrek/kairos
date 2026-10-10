"use client";

import { Alert, Button, Tooltip } from "@heroui/react";
import { Bell, BellOff, X } from "lucide-react";

import { NotificationGuide } from "@/src/pwa/notification-guide";
import {
  type NotificationState,
  useCustomerNotifications,
} from "@/src/pwa/notification-provider";

export function NotificationIcon({ enabled }: { enabled: boolean }) {
  return (
    <span
      aria-hidden="true"
      className="notification-icon"
      data-enabled={enabled}
    >
      <Bell className="notification-icon-off" size={20} />
      <BellOff className="notification-icon-on" size={20} />
    </span>
  );
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
  const {
    disable,
    dismissMessage,
    requestEnable,
    message,
    pendingAction,
    state,
  } = useCustomerNotifications();
  const isEnabled =
    pendingAction === "enabling" ||
    (state === "enabled" && pendingAction !== "disabling");
  const isUnavailable = state === "loading" || pendingAction !== null;
  const actionLabel =
    pendingAction === "enabling"
      ? "Enabling notifications…"
      : pendingAction === "disabling"
        ? "Disabling notifications…"
        : pendingAction === "requesting-permission"
          ? "Waiting for notification permission…"
          : notificationActionLabel(state);

  return (
    <>
      <Tooltip delay={500}>
        <Tooltip.Trigger>
          <Button
            isIconOnly
            aria-busy={pendingAction !== null}
            aria-label={actionLabel}
            aria-pressed={state === "enabled"}
            className="rounded-md"
            isDisabled={isUnavailable}
            variant={isEnabled ? "primary" : "tertiary"}
            onPress={() => {
              if (isEnabled) void disable();
              else requestEnable();
            }}
          >
            <NotificationIcon enabled={isEnabled} />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content>{actionLabel}</Tooltip.Content>
      </Tooltip>
      <NotificationGuide />
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

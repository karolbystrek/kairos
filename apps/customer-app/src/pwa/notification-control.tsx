"use client";

import { Alert, Button, Tooltip } from "@heroui/react";
import { ArrowUp, Bell, BellOff, X } from "lucide-react";

import { NotificationGuide } from "@/src/pwa/notification-guide";
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
  const {
    disable,
    dismissGuide,
    dismissMessage,
    hintOpen,
    requestEnable,
    message,
    state,
  } = useCustomerNotifications();
  const isEnabled = state === "enabled";
  const isUnavailable = state === "loading";
  const actionLabel = notificationActionLabel(state);

  return (
    <>
      <div className="relative">
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label={actionLabel}
              aria-pressed={isEnabled}
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
        {hintOpen && (
          <div
            className="absolute right-0 top-full z-40 mt-2 w-64 max-w-[calc(100vw-2.5rem)] rounded-xl bg-surface p-4 shadow-lg"
            role="status"
          >
            <ArrowUp aria-hidden="true" className="ml-auto mb-2" size={18} />
            <div className="flex items-start gap-2">
              <p className="text-sm">
                Tap the bell to get order notifications.
              </p>
              <Tooltip delay={500}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    aria-label="Dismiss notification hint"
                    size="sm"
                    variant="tertiary"
                    onPress={dismissGuide}
                  >
                    <X aria-hidden="true" size={16} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Dismiss</Tooltip.Content>
              </Tooltip>
            </div>
          </div>
        )}
      </div>
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

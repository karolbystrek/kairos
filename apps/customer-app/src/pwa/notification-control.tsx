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
      return "Wyłącz powiadomienia";
    case "blocked":
      return "Powiadomienia są zablokowane";
    case "unsupported":
      return "Powiadomienia nie są obsługiwane";
    case "loading":
      return "Wczytywanie ustawień powiadomień";
    default:
      return "Włącz powiadomienia";
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
      ? "Włączanie powiadomień…"
      : pendingAction === "disabling"
        ? "Wyłączanie powiadomień…"
        : pendingAction === "requesting-permission"
          ? "Oczekiwanie na zgodę na powiadomienia…"
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
                    aria-label="Zamknij komunikat"
                    className="notification-dismiss rounded-md"
                    variant="tertiary"
                    onPress={dismissMessage}
                  >
                    <X aria-hidden="true" size={18} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Zamknij</Tooltip.Content>
              </Tooltip>
            </Alert>
          </div>
        </div>
      )}
    </>
  );
}

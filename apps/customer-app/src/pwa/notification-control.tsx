"use client";

import { Alert, Button } from "@heroui/react";

import {
  type NotificationState,
  useCustomerNotifications,
} from "@/src/pwa/notification-provider";

function NotificationIcon({ enabled }: { enabled: boolean }) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height="20"
      viewBox="0 0 24 24"
      width="20"
      xmlns="http://www.w3.org/2000/svg"
    >
      {enabled ? (
        <>
          <path
            d="M6 8a6 6 0 0 1 12 0c0 7 3 7 3 9H3c0-2 3-2 3-9Z"
            stroke="currentColor"
            strokeLinejoin="round"
            strokeWidth="1.75"
          />
          <path
            d="M10 21h4"
            stroke="currentColor"
            strokeLinecap="round"
            strokeWidth="1.75"
          />
        </>
      ) : (
        <>
          <path
            d="M13.73 21a2 2 0 0 1-3.46 0M18.63 18H3a3 3 0 0 0 3-3V8c0-.6.09-1.18.25-1.73M18 8a6 6 0 0 0-8.23-5.58M3 3l18 18"
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="1.75"
          />
        </>
      )}
    </svg>
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
  const { disable, enable, message, state } = useCustomerNotifications();
  const isEnabled = state === "enabled";
  const isUnavailable = state === "loading" || state === "unsupported";
  const actionLabel = notificationActionLabel(state);

  return (
    <>
      <Button
        isIconOnly
        aria-label={actionLabel}
        aria-pressed={isEnabled}
        className="fixed right-4 top-4 z-50"
        isDisabled={isUnavailable || state === "blocked"}
        variant={isEnabled ? "primary" : "secondary"}
        onPress={() => {
          void (isEnabled ? disable() : enable());
        }}
      >
        <NotificationIcon enabled={isEnabled} />
      </Button>
      {message && (
        <Alert
          className="mb-6"
          status={
            state === "blocked" || state === "error" ? "warning" : "default"
          }
        >
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Notifications</Alert.Title>
            <Alert.Description>{message}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}
    </>
  );
}

"use client";

import { Alert, Button } from "@heroui/react";

import { useCustomerNotifications } from "@/src/pwa/notification-provider";

export function NotificationControl({
  primary = false,
}: {
  primary?: boolean;
}) {
  const { disable, enable, message, state } = useCustomerNotifications();

  if (state === "loading") {
    return null;
  }

  return (
    <div className="flex w-full flex-col items-start gap-3">
      {message && (
        <Alert
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
      {state === "enabled" ? (
        <Button
          variant="secondary"
          onPress={() => {
            void disable();
          }}
        >
          Disable notifications
        </Button>
      ) : (
        state !== "unsupported" &&
        state !== "blocked" && (
          <Button
            variant={primary ? "primary" : "secondary"}
            onPress={() => {
              void enable();
            }}
          >
            Enable notifications
          </Button>
        )
      )}
    </div>
  );
}

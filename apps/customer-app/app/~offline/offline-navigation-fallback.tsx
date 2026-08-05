"use client";

import { Alert, Button, Chip, Spinner } from "@heroui/react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { orderStatusLabels } from "@/src/orders/order-status";
import {
  readTrackedOrder,
  rememberLastStableDestination,
  type StoredTrackedOrder,
} from "@/src/pwa/storage";

export function OfflineNavigationFallback() {
  const router = useRouter();
  const [snapshot, setSnapshot] = useState<
    StoredTrackedOrder | null | undefined
  >(undefined);

  useEffect(() => {
    const match = window.location.pathname.match(/^\/orders\/([^/]+)$/);
    const trackingReference = match?.[1] ? decodeURIComponent(match[1]) : null;

    if (!trackingReference) {
      void Promise.resolve(null).then(setSnapshot);

      return;
    }
    void readTrackedOrder(trackingReference).then(setSnapshot);
  }, []);

  if (snapshot === undefined) {
    return <Spinner aria-label="Loading saved order status" />;
  }

  if (!snapshot) {
    return (
      <Alert status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>You are offline</Alert.Title>
          <Alert.Description>
            No saved status is available for this order. Reconnect to check its
            status.
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }

  return (
    <section className="flex flex-col items-start gap-4">
      <Button
        isIconOnly
        aria-label="Go to Your orders"
        className="fixed right-16 top-4 z-50"
        variant="secondary"
        onPress={() => {
          void rememberLastStableDestination({ kind: "home" }).then(() => {
            router.push("/");
          });
        }}
      >
        <svg
          aria-hidden="true"
          fill="none"
          height="20"
          viewBox="0 0 24 24"
          width="20"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M3 10.75 12 3l9 7.75V21a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1V10.75Z"
            stroke="currentColor"
            strokeLinejoin="round"
            strokeWidth="1.75"
          />
        </svg>
      </Button>
      <p className="text-sm text-warning">
        You&apos;re offline. Showing the status from{" "}
        {new Date(snapshot.updatedAt).toLocaleString()}.
      </p>
      <h1 className="text-3xl font-semibold">Order {snapshot.label}</h1>
      <Chip
        color={snapshot.status === "READY" ? "success" : "default"}
        size="lg"
      >
        {orderStatusLabels[snapshot.status]}
      </Chip>
    </section>
  );
}

"use client";

import { Alert, Chip, Spinner } from "@heroui/react";
import { useEffect, useState } from "react";

import { orderStatusLabels } from "@/src/orders/order-status";
import { readTrackedOrder, type StoredTrackedOrder } from "@/src/pwa/storage";

export function OfflineNavigationFallback() {
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

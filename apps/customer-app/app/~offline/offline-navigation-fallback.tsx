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
    return <Spinner aria-label="Loading offline order snapshot" />;
  }

  if (!snapshot) {
    return (
      <Alert status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>You are offline</Alert.Title>
          <Alert.Description>
            This order has no last-known snapshot on this device. Reconnect to
            retrieve its status.
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }

  return (
    <section className="flex flex-col items-start gap-4">
      <Alert status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Offline snapshot</Alert.Title>
          <Alert.Description>
            This status was last updated{" "}
            {new Date(snapshot.updatedAt).toLocaleString()}. Reconnect for the
            current status.
          </Alert.Description>
        </Alert.Content>
      </Alert>
      <div>
        <h1 className="text-3xl font-semibold">Order {snapshot.label}</h1>
        <p className="text-muted">Last known order status</p>
      </div>
      <Chip
        color={snapshot.status === "READY" ? "success" : "default"}
        size="lg"
      >
        {orderStatusLabels[snapshot.status]}
      </Chip>
    </section>
  );
}

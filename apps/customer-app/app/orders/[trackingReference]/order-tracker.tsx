"use client";

import { Alert, Button, Chip, Spinner } from "@heroui/react";
import { useState } from "react";
import useSWR from "swr";
import useSWRSubscription from "swr/subscription";

import {
  ApiError,
  getTrackedOrder,
  orderStatusChangedEventSchema,
  type CustomerOrder,
  type OrderStatus,
} from "@/src/api/orders";

const statusLabels: Record<OrderStatus, string> = {
  IN_PREPARATION: "In preparation",
  READY: "Ready for pickup",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

function getTrackingErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return "This order could not be found.";
  }

  return "The latest order status could not be loaded.";
}

function isActive(order: CustomerOrder | undefined): boolean {
  return order?.status === "IN_PREPARATION" || order?.status === "READY";
}

function useOrderEventStream({
  enabled,
  trackingReference,
  revalidate,
  setConnected,
}: {
  enabled: boolean;
  trackingReference: string;
  revalidate: () => Promise<CustomerOrder | undefined>;
  setConnected: (connected: boolean) => void;
}) {
  useSWRSubscription(
    enabled ? (["tracked-order-events", trackingReference] as const) : null,
    ([, reference]) => {
      const eventSource = new EventSource(
        `/api/tracked-orders/${encodeURIComponent(reference)}/events`,
      );

      eventSource.onopen = () => {
        setConnected(true);
        void revalidate();
      };
      eventSource.onerror = () => {
        setConnected(false);
      };
      const statusChanged = (event: MessageEvent<string>) => {
        try {
          const parsed = orderStatusChangedEventSchema.safeParse(
            JSON.parse(event.data),
          );

          if (parsed.success && parsed.data.trackingReference === reference) {
            void revalidate();
          }
        } catch {
          // Invalid events are ignored; REST remains authoritative.
        }
      };

      eventSource.addEventListener("order-status-changed", statusChanged);

      return () => {
        eventSource.removeEventListener("order-status-changed", statusChanged);
        eventSource.close();
      };
    },
  );
}

export function OrderTracker({
  trackingReference,
}: {
  trackingReference: string;
}) {
  const [isStreamConnected, setIsStreamConnected] = useState(false);
  const {
    data: order,
    error,
    isLoading,
    isValidating,
    mutate,
  } = useSWR(
    ["tracked-order", trackingReference] as const,
    ([, reference]) => getTrackedOrder(reference),
    {
      errorRetryCount: 3,
      // Invalidation clears SWR's cache before refetching. Retain the last
      // authoritative order so that stream ownership does not flap meanwhile.
      keepPreviousData: true,
      refreshInterval: (latestOrder) =>
        isActive(latestOrder) && !isStreamConnected ? 15_000 : 0,
      shouldRetryOnError,
    },
  );
  const isOrderActive = isActive(order);

  useOrderEventStream({
    enabled: isOrderActive,
    trackingReference,
    revalidate: () => mutate(undefined, { throwOnError: false }),
    setConnected: setIsStreamConnected,
  });

  if (isLoading && !order) {
    return <Spinner aria-label="Loading order" />;
  }

  if (!order) {
    return (
      <Alert status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Order unavailable</Alert.Title>
          <Alert.Description>
            {getTrackingErrorMessage(error)}
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }

  return (
    <section className="flex flex-col items-start gap-4">
      {error && (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Status may be out of date</Alert.Title>
            <Alert.Description>
              {getTrackingErrorMessage(error)} Showing the last known status.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}
      <div>
        <h1 className="text-3xl font-semibold">Order {order.label}</h1>
        <p className="text-muted">Current order status</p>
      </div>
      <Chip color={order.status === "READY" ? "success" : "default"} size="lg">
        {statusLabels[order.status]}
      </Chip>
      <p className="text-sm text-muted">
        Updated {new Date(order.updatedAt).toLocaleString()}
      </p>
      <Button
        isDisabled={isValidating}
        variant="secondary"
        onPress={() => {
          void mutate(undefined, { throwOnError: false });
        }}
      >
        {isValidating ? "Refreshing…" : "Refresh"}
      </Button>
    </section>
  );
}

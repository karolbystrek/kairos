"use client";

import { Alert, Button, Chip, Spinner } from "@heroui/react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import useSWR from "swr";
import useSWRSubscription from "swr/subscription";

import {
  ApiError,
  getTrackedOrder,
  orderStatusChangedEventSchema,
  type CustomerOrder,
} from "@/src/api/orders";
import {
  isActiveOrderStatus,
  orderStatusLabels,
} from "@/src/orders/order-status";
import { rememberTrackedOrder } from "@/src/pwa/recently-tracked-orders";

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
  return isActiveOrderStatus(order?.status);
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
  const router = useRouter();
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

  useEffect(() => {
    if (!order) {
      return;
    }

    rememberTrackedOrder({
      trackingReference,
      label: order.label,
      status: order.status,
      updatedAt: order.updatedAt,
    });
  }, [order, trackingReference]);

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
      <div className="flex w-full items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Order {order.label}</h1>
          <p className="text-muted">Current order status</p>
        </div>
        {!isOrderActive && (
          <Button
            isIconOnly
            aria-label="Go to recently tracked orders"
            variant="secondary"
            onPress={() => {
              router.push("/");
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
        )}
      </div>
      <Chip color={order.status === "READY" ? "success" : "default"} size="lg">
        {orderStatusLabels[order.status]}
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

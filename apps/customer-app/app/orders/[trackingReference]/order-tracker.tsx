"use client";

import { Alert, Button, Chip, Spinner } from "@heroui/react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import useSWRSubscription from "swr/subscription";

import {
  ApiError,
  getTrackedOrder,
  orderStatusChangedEventSchema,
  type CustomerOrder,
} from "@/src/api/orders";
import { apiUrl } from "@/src/api/api-url";
import {
  isActiveOrderStatus,
  orderStatusLabels,
} from "@/src/orders/order-status";
import { updateApplicationBadge } from "@/src/pwa/badge";
import { useCustomerNotifications } from "@/src/pwa/notification-provider";
import {
  readTrackedOrder,
  rememberTrackedOrder,
  removeTrackedOrder,
  type StoredTrackedOrder,
} from "@/src/pwa/storage";

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
        apiUrl(
          `/api/tracked-orders/v1/${encodeURIComponent(reference)}/events`,
        ),
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
  const { enrollOrder } = useCustomerNotifications();
  const [isStreamConnected, setIsStreamConnected] = useState(false);
  const [offlineOrder, setOfflineOrder] = useState<StoredTrackedOrder | null>(
    null,
  );
  const [offlineLookupComplete, setOfflineLookupComplete] = useState(false);
  const [isOnline, setIsOnline] = useState(true);
  const previousTransition = useRef<string | null>(null);
  const {
    data: order,
    error,
    isLoading,
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
  const displayedOrder = order ?? offlineOrder;
  const isOfflineSnapshot = !order && offlineOrder !== null;

  useEffect(() => {
    const synchronizeConnectivity = () => {
      setIsOnline(navigator.onLine);
    };

    synchronizeConnectivity();
    window.addEventListener("online", synchronizeConnectivity);
    window.addEventListener("offline", synchronizeConnectivity);

    return () => {
      window.removeEventListener("online", synchronizeConnectivity);
      window.removeEventListener("offline", synchronizeConnectivity);
    };
  }, []);

  useEffect(() => {
    if (!order) {
      return;
    }

    const transitionIdentity = `${order.status}:${order.updatedAt}`;

    if (
      previousTransition.current !== null &&
      previousTransition.current !== transitionIdentity
    ) {
      try {
        navigator.vibrate?.(100);
      } catch {
        // Foreground vibration is a best-effort progressive enhancement.
      }
    }
    previousTransition.current = transitionIdentity;
    void rememberTrackedOrder({
      trackingReference,
      label: order.label,
      status: order.status,
      updatedAt: order.updatedAt,
    }).then(async () => {
      await updateApplicationBadge();
      if (isActiveOrderStatus(order.status)) {
        await enrollOrder(trackingReference);
      }
    });
  }, [enrollOrder, order, trackingReference]);

  useEffect(() => {
    if (!error || order) {
      return;
    }
    let active = true;

    void readTrackedOrder(trackingReference).then((snapshot) => {
      if (active) {
        setOfflineOrder(snapshot);
        setOfflineLookupComplete(true);
        if (snapshot) {
          previousTransition.current = `${snapshot.status}:${snapshot.updatedAt}`;
        }
      }
    });

    return () => {
      active = false;
    };
  }, [error, order, trackingReference]);

  useOrderEventStream({
    enabled: isOrderActive,
    trackingReference,
    revalidate: () => mutate(undefined, { throwOnError: false }),
    setConnected: setIsStreamConnected,
  });

  if ((isLoading || (error && !offlineLookupComplete)) && !displayedOrder) {
    return <Spinner aria-label="Loading order" />;
  }

  if (!displayedOrder) {
    return (
      <Alert status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Order unavailable</Alert.Title>
          <Alert.Description>
            {!isOnline && offlineLookupComplete
              ? "You're offline and no saved status is available for this order. Reconnect to check its status."
              : getTrackingErrorMessage(error)}
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }

  return (
    <section className="flex flex-col items-start gap-4">
      {(error || isOfflineSnapshot) && (
        <p className="text-sm text-warning">
          {!isOnline
            ? `You're offline. Showing the status from ${new Date(displayedOrder.updatedAt).toLocaleString()}.`
            : `Status may be out of date. Showing the status from ${new Date(displayedOrder.updatedAt).toLocaleString()}.`}
        </p>
      )}
      <div className="flex w-full items-start justify-between gap-4">
        <h1 className="text-3xl font-semibold">Order {displayedOrder.label}</h1>
        {!isActiveOrderStatus(displayedOrder.status) && (
          <Button
            isIconOnly
            aria-label="Go to recently tracked orders"
            className="fixed right-16 top-4 z-50"
            variant="secondary"
            onPress={() => {
              void removeTrackedOrder(
                trackingReference,
                "terminal",
                displayedOrder.status,
              ).then(() => {
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
        )}
      </div>
      <Chip
        color={displayedOrder.status === "READY" ? "success" : "default"}
        size="lg"
      >
        {orderStatusLabels[displayedOrder.status]}
      </Chip>
    </section>
  );
}

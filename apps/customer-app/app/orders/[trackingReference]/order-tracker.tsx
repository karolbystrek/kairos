"use client";

import { Alert, Spinner } from "@heroui/react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import useSWRSubscription from "swr/subscription";

import { CustomerToolbar } from "@/components/customer-toolbar";
import {
  ApiError,
  getTrackedOrder,
  orderStatusChangedEventSchema,
  type CustomerOrder,
} from "@/src/api/orders";
import { apiUrl } from "@/src/api/api-url";
import { isActiveOrderStatus } from "@/src/orders/order-status";
import { updateApplicationBadge } from "@/src/pwa/badge";
import { useCustomerNotifications } from "@/src/pwa/notification-provider";
import {
  readTrackedOrder,
  rememberTrackedOrder,
  rememberLastStableDestination,
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

const statusCopy = {
  IN_PREPARATION: "We’re preparing your order",
  READY: "Ready for pickup",
  COMPLETED: "Order complete",
  CANCELED: "Order canceled",
} as const;

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
  const leavingForHome = useRef(false);
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
  const goHome = () => {
    leavingForHome.current = true;
    void rememberLastStableDestination({ kind: "home" }).then(() => {
      router.push("/");
    });
  };

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
      if (!leavingForHome.current) {
        await rememberLastStableDestination(
          isActiveOrderStatus(order.status)
            ? { kind: "order", trackingReference }
            : { kind: "home" },
        );
      }
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
    return (
      <section className="flex min-h-[calc(100svh-3rem)] flex-col">
        <CustomerToolbar onHome={goHome} />
        <div className="flex flex-1 items-center justify-center">
          <Spinner aria-label="Loading order" />
        </div>
      </section>
    );
  }

  if (!displayedOrder) {
    return (
      <section className="flex min-h-[calc(100svh-3rem)] flex-col">
        <CustomerToolbar onHome={goHome} />
        <div className="flex flex-1 items-center justify-center">
          <Alert className="w-full" status="danger">
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
        </div>
      </section>
    );
  }

  return (
    <section className="flex min-h-[calc(100svh-3rem)] flex-col">
      <CustomerToolbar onHome={goHome} />
      {(error || isOfflineSnapshot) && (
        <Alert className="mt-4" status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>
              {isOnline ? "Status may be outdated" : "You’re offline"}
            </Alert.Title>
            <Alert.Description>
              Last known status from{" "}
              {new Date(displayedOrder.updatedAt).toLocaleString()}.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}
      <div
        aria-live="polite"
        className="flex flex-1 flex-col items-center justify-center py-12 text-center sm:py-16"
      >
        <p className="text-sm secondary-text">Order {displayedOrder.label}</p>
        <h1 className="status-title mt-4 max-w-[14ch]">
          {statusCopy[displayedOrder.status]}
        </h1>
      </div>
    </section>
  );
}

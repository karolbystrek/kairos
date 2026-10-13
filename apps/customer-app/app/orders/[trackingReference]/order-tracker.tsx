"use client";

import { Alert, Button } from "@heroui/react";
import { ScanIcon, XIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import useSWR, { unstable_serialize, useSWRConfig } from "swr";
import useSWRSubscription from "swr/subscription";
import { z } from "zod";

import { OrderLoadingIndicator } from "@/components/order-loading-indicator";
import { CustomerToolbar } from "@/components/customer-toolbar";
import {
  ApiError,
  getTrackedOrder,
  customerOrderSchema,
  orderStatusChangedEventSchema,
  type CustomerOrder,
} from "@/src/api/orders";
import { apiUrl } from "@/src/api/api-url";
import { createOrderState } from "@/src/orders/order-state";
import { orderStatusSchema } from "@/src/orders/order-status";
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
  if (
    error instanceof ApiError &&
    (error.status === 400 || error.status === 404)
  ) {
    return "Nie znaleziono tego zamówienia.";
  }

  return "Nie udało się pobrać aktualnego statusu zamówienia.";
}

function isActive(order: CustomerOrder | undefined): boolean {
  return isActiveOrderStatus(order?.status);
}

const statusCopy = {
  IN_PREPARATION: "Przygotowujemy Twoje zamówienie",
  READY: "Gotowe do odbioru",
  COMPLETED: "Zamówienie zrealizowane",
  CANCELED: "Zamówienie anulowane",
} as const;

function useOrderEventStream({
  enabled,
  trackingReference,
  applyOrder,
  setConnected,
}: {
  enabled: boolean;
  trackingReference: string;
  applyOrder: (order: CustomerOrder) => void;
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
            applyOrder(parsed.data);
          }
        } catch {
          // Malformed stream data must not enter the customer state.
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
  const { enrollOrder, showGuideForOrder } = useCustomerNotifications();
  const [isStreamConnected, setIsStreamConnected] = useState(false);
  const [offlineOrder, setOfflineOrder] = useState<StoredTrackedOrder | null>(
    null,
  );
  const [offlineLookupComplete, setOfflineLookupComplete] = useState(false);
  const [isOnline, setIsOnline] = useState(true);
  const destinationSaved = useRef<Promise<void>>(Promise.resolve());
  const previousTransition = useRef<string | null>(null);
  const { cache } = useSWRConfig();
  const [orderState] = useState(() => {
    const cached = customerOrderSchema.safeParse(
      cache.get(unstable_serialize(["tracked-order", trackingReference]))?.data,
    );

    return createOrderState(cached.success ? cached.data : undefined);
  });
  const {
    data: order,
    error,
    isLoading,
    mutate,
  } = useSWR(
    ["tracked-order", trackingReference] as const,
    async ([, reference]) => {
      const stored = await readTrackedOrder(reference);

      if (stored) orderState.accept(stored);

      return orderState.accept(await getTrackedOrder(reference));
    },
    {
      errorRetryCount: 3,
      // Retain the last state while fallback or focus requests are in flight.
      keepPreviousData: true,
      refreshInterval: (latestOrder) =>
        isActive(latestOrder) && !isStreamConnected ? 15_000 : 0,
      shouldRetryOnError,
    },
  );
  const isOrderActive = isActive(order);
  const isOrderUnavailable =
    error instanceof ApiError && (error.status === 400 || error.status === 404);
  const displayedOrder = order ?? offlineOrder;
  const isOfflineSnapshot = !order && offlineOrder !== null;
  const closeOrder = async () => {
    await destinationSaved.current;
    await rememberLastStableDestination({ kind: "home" });
    router.replace("/");
  };

  useEffect(() => {
    destinationSaved.current = rememberLastStableDestination({
      kind: "order",
      trackingReference,
    });
  }, [trackingReference]);

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

  const hasLoadedOrder = Boolean(order);

  useEffect(() => {
    if (!hasLoadedOrder) return;
    const timer = window.setTimeout(showGuideForOrder, 3000);

    return () => window.clearTimeout(timer);
  }, [hasLoadedOrder, showGuideForOrder, trackingReference]);

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
      number: order.number,
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

    void (
      isOrderUnavailable
        ? Promise.resolve(null)
        : readTrackedOrder(trackingReference)
    ).then((snapshot) => {
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
  }, [error, isOrderUnavailable, order, trackingReference]);

  useOrderEventStream({
    enabled: isOrderActive,
    trackingReference,
    applyOrder: (candidate) => {
      void mutate(
        (current) => {
          if (current) orderState.accept(current);

          return orderState.accept(candidate);
        },
        { revalidate: false },
      );
    },
    setConnected: setIsStreamConnected,
  });

  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    const receiveTransition = (event: MessageEvent<unknown>) => {
      const result = z
        .object({
          type: z.literal("KAIROS_ORDER_TRANSITION"),
          trackingReference: z.uuid(),
          status: orderStatusSchema,
          transitionedAt: z.iso.datetime({ offset: true }),
        })
        .safeParse(event.data);

      if (
        !result.success ||
        result.data.trackingReference !== trackingReference
      )
        return;
      const transition = {
        status: result.data.status,
        updatedAt: result.data.transitionedAt,
      };
      const next = orderState.transition(transition);

      if (next) void mutate(next, { revalidate: false });
    };

    navigator.serviceWorker.addEventListener("message", receiveTransition);

    return () =>
      navigator.serviceWorker.removeEventListener("message", receiveTransition);
  }, [mutate, orderState, trackingReference]);

  if ((isLoading || (error && !offlineLookupComplete)) && !displayedOrder) {
    return (
      <section className="flex flex-1 flex-col">
        <CustomerToolbar />
        <OrderLoadingIndicator label="Wczytywanie zamówienia" />
      </section>
    );
  }

  if (!displayedOrder) {
    return (
      <section className="flex flex-1 flex-col">
        <CustomerToolbar />
        <div className="flex flex-1 items-center justify-center">
          <Alert className="w-full" status="danger">
            <Alert.Indicator />
            <Alert.Content>
              <Alert.Title>Zamówienie niedostępne</Alert.Title>
              <Alert.Description>
                {!isOnline && offlineLookupComplete
                  ? "Brak połączenia i zapisanego statusu tego zamówienia. Połącz się z internetem, aby sprawdzić status."
                  : getTrackingErrorMessage(error)}
              </Alert.Description>
              {isOrderUnavailable && (
                <Button
                  className="mt-4"
                  variant="secondary"
                  onPress={() => void closeOrder()}
                >
                  <ScanIcon aria-hidden="true" size={18} />
                  Wróć do skanera
                </Button>
              )}
            </Alert.Content>
          </Alert>
        </div>
      </section>
    );
  }

  return (
    <section className="flex flex-1 flex-col">
      <CustomerToolbar />
      {(error || isOfflineSnapshot) && (
        <Alert className="mt-4" status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>
              {isOnline
                ? "Status może być nieaktualny"
                : "Brak połączenia z internetem"}
            </Alert.Title>
            <Alert.Description>
              Ostatni znany status z{" "}
              {new Date(displayedOrder.updatedAt).toLocaleString("pl-PL")}.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}
      <div
        aria-live="polite"
        className="flex flex-1 flex-col items-center justify-center py-12 text-center sm:py-16"
      >
        <p className="text-4xl font-semibold tabular-nums">
          <span className="sr-only">Zamówienie </span>#{displayedOrder.number}
        </p>
        {displayedOrder.label && (
          <p className="mt-1 max-w-md break-words text-sm secondary-text">
            {displayedOrder.label}
          </p>
        )}
        <h1 className="status-title mt-4 max-w-[14ch]">
          {statusCopy[displayedOrder.status]}
        </h1>
        {!isActiveOrderStatus(displayedOrder.status) && (
          <Button
            className="mt-8"
            variant="secondary"
            onPress={() => void closeOrder()}
          >
            <XIcon aria-hidden="true" size={20} />
            Zamknij zamówienie
          </Button>
        )}
      </div>
    </section>
  );
}

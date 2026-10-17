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
import { OrderSummary } from "@/components/order-summary";
import {
  ApiError,
  getTrackedOrder,
  customerOrderSchema,
  orderStatusChangedEventSchema,
  type CustomerOrder,
} from "@/src/api/orders";
import { apiUrl } from "@/src/api/api-url";
import { createOrderState } from "@/src/orders/order-state";
import {
  isActiveOrderStatus,
  orderStatusSchema,
} from "@/src/orders/order-status";
import { updateApplicationBadge } from "@/src/pwa/badge";
import { useCustomerNotifications } from "@/src/pwa/notification-provider";
import {
  readTrackedOrder,
  rememberTrackedOrder,
  rememberLastStableDestination,
} from "@/src/pwa/storage";
import { useOnline } from "@/src/pwa/use-online";

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

const reconnectDelays = { initial: 2_000, maximum: 30_000, attempts: 10 };

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
      const url = apiUrl(
        `/api/tracked-orders/v1/${encodeURIComponent(reference)}/events`,
      );
      let eventSource: EventSource;
      let timer: number | undefined;
      let delay = reconnectDelays.initial;
      let attempts = 0;
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
      const connect = () => {
        eventSource = new EventSource(url);
        eventSource.onopen = () => {
          delay = reconnectDelays.initial;
          attempts = 0;
          setConnected(true);
        };
        eventSource.onerror = () => {
          setConnected(false);
          // A closed source (HTTP error) never retries by itself.
          if (eventSource.readyState !== EventSource.CLOSED) return;
          eventSource.close();
          // Persistent refusals (404, 429) leave the order to the polling fallback.
          if (++attempts > reconnectDelays.attempts) return;
          timer = window.setTimeout(connect, delay);
          delay = Math.min(delay * 2, reconnectDelays.maximum);
        };
        eventSource.addEventListener("order-status-changed", statusChanged);
      };

      connect();

      return () => {
        window.clearTimeout(timer);
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
  const isOnline = useOnline();
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
  const { data: offlineOrder = null, isLoading: isOfflineLookupLoading } =
    useSWR(
      error && !order && !isOrderUnavailable
        ? (["tracked-order-snapshot", trackingReference] as const)
        : null,
      ([, reference]) => readTrackedOrder(reference),
      {
        onSuccess: (snapshot) => {
          if (snapshot) {
            previousTransition.current = `${snapshot.status}:${snapshot.updatedAt}`;
          }
        },
      },
    );
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

  if ((isLoading || isOfflineLookupLoading) && !displayedOrder) {
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
                {!isOnline && error
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
    <OrderSummary
      order={displayedOrder}
      staleWarning={
        error || isOfflineSnapshot
          ? isOnline
            ? "Status może być nieaktualny"
            : "Brak połączenia z internetem"
          : null
      }
    >
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
    </OrderSummary>
  );
}

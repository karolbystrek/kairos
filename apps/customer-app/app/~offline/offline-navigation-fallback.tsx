"use client";

import { Alert } from "@heroui/react";
import { useEffect, useState, useSyncExternalStore } from "react";

import { OrderLoadingIndicator } from "@/components/order-loading-indicator";
import { CustomerToolbar } from "@/components/customer-toolbar";
import { OrderSummary } from "@/components/order-summary";
import { CustomerHome } from "@/app/customer-home";
import {
  readTrackedOrder,
  rememberLastStableDestination,
  type StoredTrackedOrder,
} from "@/src/pwa/storage";

// The fallback is served for whichever URL failed, so the path never changes.
const subscribeToPathname = () => () => {};

export function OfflineNavigationFallback() {
  const pathname = useSyncExternalStore(
    subscribeToPathname,
    () => window.location.pathname,
    () => null,
  );
  const match = pathname?.match(/^\/orders\/([^/]+)$/);
  const trackingReference = match?.[1] ? decodeURIComponent(match[1]) : null;
  const [snapshot, setSnapshot] = useState<
    StoredTrackedOrder | null | undefined
  >(undefined);

  useEffect(() => {
    const resumeTracking = () => window.location.reload();

    window.addEventListener("online", resumeTracking);

    return () => window.removeEventListener("online", resumeTracking);
  }, []);

  useEffect(() => {
    if (!trackingReference) return;
    void rememberLastStableDestination({ kind: "order", trackingReference })
      .then(() => readTrackedOrder(trackingReference))
      .then(setSnapshot);
  }, [trackingReference]);

  if (pathname !== null && !trackingReference) {
    return <CustomerHome installationTrackingReference={null} />;
  }

  if (snapshot === undefined) {
    return (
      <section className="flex flex-1 flex-col">
        <CustomerToolbar />
        <OrderLoadingIndicator label="Wczytywanie zapisanego statusu zamówienia" />
      </section>
    );
  }

  if (!snapshot) {
    return (
      <section className="flex flex-1 flex-col">
        <CustomerToolbar />
        <div className="flex flex-1 items-center justify-center">
          <Alert className="w-full" status="warning">
            <Alert.Indicator />
            <Alert.Content>
              <Alert.Title>Brak połączenia z internetem</Alert.Title>
              <Alert.Description>
                Brak zapisanego statusu tego zamówienia. Połącz się z
                internetem, aby sprawdzić status.
              </Alert.Description>
            </Alert.Content>
          </Alert>
        </div>
      </section>
    );
  }

  return (
    <OrderSummary
      order={snapshot}
      staleWarning="Brak połączenia z internetem"
    />
  );
}

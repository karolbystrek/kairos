"use client";

import { Alert, Spinner } from "@heroui/react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { CustomerToolbar } from "@/components/customer-toolbar";
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
    return (
      <section className="flex min-h-[calc(100svh-3rem)] flex-col">
        <CustomerToolbar />
        <div className="flex flex-1 items-center justify-center">
          <Spinner aria-label="Loading saved order status" />
        </div>
      </section>
    );
  }

  if (!snapshot) {
    return (
      <section className="flex min-h-[calc(100svh-3rem)] flex-col">
        <CustomerToolbar
          onHome={() => {
            void rememberLastStableDestination({ kind: "home" }).then(() => {
              router.push("/");
            });
          }}
        />
        <div className="flex flex-1 items-center justify-center">
          <Alert className="w-full" status="warning">
            <Alert.Indicator />
            <Alert.Content>
              <Alert.Title>You are offline</Alert.Title>
              <Alert.Description>
                No saved status is available for this order. Reconnect to check
                its status.
              </Alert.Description>
            </Alert.Content>
          </Alert>
        </div>
      </section>
    );
  }

  return (
    <section className="flex min-h-[calc(100svh-3rem)] flex-col">
      <CustomerToolbar
        onHome={() => {
          void rememberLastStableDestination({ kind: "home" }).then(() => {
            router.push("/");
          });
        }}
      />
      <Alert className="mt-4" status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>You’re offline</Alert.Title>
          <Alert.Description>
            Last known status from{" "}
            {new Date(snapshot.updatedAt).toLocaleString()}.
          </Alert.Description>
        </Alert.Content>
      </Alert>
      <div className="flex flex-1 flex-col items-center justify-center py-12 text-center">
        <p className="text-sm secondary-text">Order {snapshot.label}</p>
        <h1 className="status-title mt-4 max-w-[14ch]">
          {orderStatusLabels[snapshot.status]}
        </h1>
      </div>
    </section>
  );
}

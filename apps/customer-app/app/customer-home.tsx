"use client";

import { AlertDialog, Button, Card, Chip, Spinner } from "@heroui/react";
import NextLink from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";

import {
  isActiveOrderStatus,
  orderStatusLabels,
} from "@/src/orders/order-status";
import {
  clearRecentlyTrackedOrders,
  consumeInstallationBootstrap,
  readRecentlyTrackedOrders,
  type RecentlyTrackedOrder,
} from "@/src/pwa/recently-tracked-orders";

function subscribeToHydration(): () => void {
  return () => undefined;
}

function getClientHydrationSnapshot(): boolean {
  return true;
}

function getServerHydrationSnapshot(): boolean {
  return false;
}

function isStandaloneDisplayMode(): boolean {
  const navigatorWithStandalone = navigator as Navigator & {
    standalone?: boolean;
  };

  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    navigatorWithStandalone.standalone === true
  );
}

function getTrackedOrderHref(trackingReference: string): string {
  return `/orders/${encodeURIComponent(trackingReference)}`;
}

function OrderSummaryCard({ order }: { order: RecentlyTrackedOrder }) {
  return (
    <NextLink
      className="block rounded-3xl no-underline outline-none focus-visible:ring-2 focus-visible:ring-accent"
      href={getTrackedOrderHref(order.trackingReference)}
    >
      <Card className="w-full gap-4 transition-colors hover:bg-surface-secondary">
        <Card.Header className="flex-row items-start justify-between gap-4">
          <div className="min-w-0">
            <Card.Title className="truncate text-lg">
              Order {order.label}
            </Card.Title>
            <Card.Description>
              Updated {new Date(order.updatedAt).toLocaleString()}
            </Card.Description>
          </div>
          <Chip
            color={order.status === "READY" ? "success" : "default"}
            size="sm"
          >
            {orderStatusLabels[order.status]}
          </Chip>
        </Card.Header>
      </Card>
    </NextLink>
  );
}

export function CustomerHome({
  installationTrackingReference,
}: {
  installationTrackingReference: string | null;
}) {
  const router = useRouter();
  const isHydrated = useSyncExternalStore(
    subscribeToHydration,
    getClientHydrationSnapshot,
    getServerHydrationSnapshot,
  );
  const [ordersOverride, setOrdersOverride] = useState<
    RecentlyTrackedOrder[] | null
  >(null);
  const hasResolvedInstalledLaunch = useRef(false);
  const recentlyTrackedOrders = isHydrated
    ? (ordersOverride ?? readRecentlyTrackedOrders())
    : [];
  const mostRecentlyTrackedOrder = recentlyTrackedOrders[0];

  useEffect(() => {
    if (!isHydrated || hasResolvedInstalledLaunch.current) {
      return;
    }

    hasResolvedInstalledLaunch.current = true;

    if (!isStandaloneDisplayMode()) {
      return;
    }

    const installationOrder = consumeInstallationBootstrap(
      installationTrackingReference,
    );

    if (installationOrder) {
      router.replace(getTrackedOrderHref(installationOrder));

      return;
    }

    if (isActiveOrderStatus(mostRecentlyTrackedOrder?.status)) {
      router.replace(
        getTrackedOrderHref(mostRecentlyTrackedOrder.trackingReference),
      );

      return;
    }

    if (installationTrackingReference) {
      router.replace("/");
    }
  }, [
    installationTrackingReference,
    isHydrated,
    mostRecentlyTrackedOrder,
    router,
  ]);

  if (!isHydrated) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner aria-label="Loading recently tracked orders" />
      </div>
    );
  }

  if (recentlyTrackedOrders.length === 0) {
    return (
      <section className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
        <h1 className="text-3xl font-semibold">Kairos Order Tracking</h1>
        <p className="max-w-sm text-muted">
          Scan the QR code provided by the restaurant to open your order.
        </p>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-semibold">Recently tracked orders</h1>
        <p className="text-muted">Select an order to load its latest status.</p>
      </div>

      <div className="flex flex-col gap-3">
        {recentlyTrackedOrders.map((order) => (
          <OrderSummaryCard key={order.trackingReference} order={order} />
        ))}
      </div>

      <AlertDialog>
        <Button className="self-start" variant="secondary">
          Clear tracked orders
        </Button>
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[400px]">
              <AlertDialog.CloseTrigger />
              <AlertDialog.Header>
                <AlertDialog.Icon status="danger" />
                <AlertDialog.Heading>
                  Clear all tracked orders?
                </AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body>
                <p>
                  This removes every order remembered in this browser or
                  installed app.
                </p>
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button slot="close" variant="tertiary">
                  Cancel
                </Button>
                <Button
                  slot="close"
                  variant="danger"
                  onPress={() => {
                    clearRecentlyTrackedOrders();
                    setOrdersOverride([]);
                  }}
                >
                  Clear all
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
    </section>
  );
}

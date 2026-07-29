"use client";

import { AlertDialog, Button, Card, Chip, Spinner } from "@heroui/react";
import NextLink from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import {
  isActiveOrderStatus,
  orderStatusLabels,
} from "@/src/orders/order-status";
import { consumeInstallationBootstrap } from "@/src/pwa/recently-tracked-orders";
import { NotificationControl } from "@/src/pwa/notification-control";
import { useCustomerNotifications } from "@/src/pwa/notification-provider";
import {
  pruneTerminalTrackedOrders,
  type StoredTrackedOrder,
} from "@/src/pwa/storage";

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

function OrderSummaryCard({ order }: { order: StoredTrackedOrder }) {
  return (
    <NextLink
      className="block no-underline outline-none focus-visible:ring-2 focus-visible:ring-accent"
      href={getTrackedOrderHref(order.trackingReference)}
    >
      <Card className="w-full">
        <Card.Header className="flex-row items-start justify-between gap-4">
          <Card.Title className="truncate text-lg">
            Order {order.label}
          </Card.Title>
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
  const { clearOrders } = useCustomerNotifications();
  const [recentlyTrackedOrders, setRecentlyTrackedOrders] = useState<
    StoredTrackedOrder[] | null
  >(null);
  const hasResolvedInstalledLaunch = useRef(false);
  const mostRecentlyTrackedOrder = recentlyTrackedOrders?.[0];

  useEffect(() => {
    let active = true;

    void pruneTerminalTrackedOrders().then((orders) => {
      if (active) {
        setRecentlyTrackedOrders(orders);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!recentlyTrackedOrders || hasResolvedInstalledLaunch.current) {
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

    if (
      mostRecentlyTrackedOrder &&
      isActiveOrderStatus(mostRecentlyTrackedOrder.status)
    ) {
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
    mostRecentlyTrackedOrder,
    recentlyTrackedOrders,
    router,
  ]);

  if (!recentlyTrackedOrders) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner aria-label="Loading recently tracked orders" />
      </div>
    );
  }

  if (recentlyTrackedOrders.length === 0) {
    return (
      <section className="flex min-h-[60vh] flex-col items-center justify-center gap-6 text-center">
        <div className="flex flex-col items-center gap-3">
          <h1 className="text-3xl font-semibold">Kairos Order Tracking</h1>
          <p className="max-w-sm text-muted">
            Scan the QR code provided by the restaurant to open your order.
          </p>
        </div>
        <div className="w-full max-w-sm text-left">
          <NotificationControl />
        </div>
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

      <NotificationControl />

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
                  installed app and stops notifications for those orders. Kairos
                  notifications remain enabled for future orders.
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
                    void clearOrders().then((cleared) => {
                      if (cleared) {
                        setRecentlyTrackedOrders([]);
                      }
                    });
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

"use client";

import type { PointerEvent as ReactPointerEvent } from "react";

import { Alert, Button, Card, Chip, Dropdown, Spinner } from "@heroui/react";
import NextLink from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { orderStatusLabels } from "@/src/orders/order-status";
import { getTrackedOrderHref } from "@/src/orders/order-route";
import { resolveInstalledLaunchHref } from "@/src/pwa/launch-destination";
import { consumeInstallationBootstrap } from "@/src/pwa/recently-tracked-orders";
import { useCustomerNotifications } from "@/src/pwa/notification-provider";
import {
  pruneTerminalTrackedOrders,
  readLastStableDestination,
  rememberLastStableDestination,
  type StableCustomerDestination,
  type StoredTrackedOrder,
} from "@/src/pwa/storage";

const STOP_ACTION_WIDTH = 132;
const SWIPE_THRESHOLD = 10;

function isStandaloneDisplayMode(): boolean {
  const navigatorWithStandalone = navigator as Navigator & {
    standalone?: boolean;
  };

  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    navigatorWithStandalone.standalone === true
  );
}

function QrScannerIcon({ size = 24 }: { size?: number }) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height={size}
      viewBox="0 0 24 24"
      width={size}
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M4 9V5a1 1 0 0 1 1-1h4M15 4h4a1 1 0 0 1 1 1v4M20 15v4a1 1 0 0 1-1 1h-4M9 20H5a1 1 0 0 1-1-1v-4M8 8h3v3H8V8Zm5 0h3v3h-3V8Zm-5 5h3v3H8v-3Zm5 0h1.5v1.5H13V13Zm1.5 1.5H16V16h-1.5v-1.5Z"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.75"
      />
    </svg>
  );
}

function MoreIcon() {
  return (
    <svg
      aria-hidden="true"
      fill="currentColor"
      height="20"
      viewBox="0 0 24 24"
      width="20"
      xmlns="http://www.w3.org/2000/svg"
    >
      <circle cx="5" cy="12" r="1.5" />
      <circle cx="12" cy="12" r="1.5" />
      <circle cx="19" cy="12" r="1.5" />
    </svg>
  );
}

type OrderSummaryCardProps = {
  isStopping: boolean;
  onStopTracking: () => void;
  order: StoredTrackedOrder;
};

function OrderSummaryCard({
  isStopping,
  onStopTracking,
  order,
}: OrderSummaryCardProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [isRevealed, setIsRevealed] = useState(false);
  const [offset, setOffset] = useState(0);
  const drag = useRef<{
    baseOffset: number;
    horizontal: boolean;
    pointerId: number;
    startX: number;
    startY: number;
  } | null>(null);
  const suppressNextClick = useRef(false);

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (
      isStopping ||
      event.button !== 0 ||
      (event.target as Element).closest("button,[role='menuitem']")
    ) {
      return;
    }

    drag.current = {
      baseOffset: isRevealed ? -STOP_ACTION_WIDTH : 0,
      horizontal: false,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const currentDrag = drag.current;

    if (!currentDrag || currentDrag.pointerId !== event.pointerId) {
      return;
    }
    const deltaX = event.clientX - currentDrag.startX;
    const deltaY = event.clientY - currentDrag.startY;

    if (!currentDrag.horizontal) {
      if (Math.max(Math.abs(deltaX), Math.abs(deltaY)) < SWIPE_THRESHOLD) {
        return;
      }
      if (Math.abs(deltaY) >= Math.abs(deltaX)) {
        drag.current = null;
        event.currentTarget.releasePointerCapture(event.pointerId);

        return;
      }
      currentDrag.horizontal = true;
      setIsDragging(true);
    }

    event.preventDefault();
    setOffset(
      Math.min(
        0,
        Math.max(-STOP_ACTION_WIDTH, currentDrag.baseOffset + deltaX),
      ),
    );
  };

  const settleSwipe = (
    event: ReactPointerEvent<HTMLDivElement>,
    canceled = false,
  ) => {
    const currentDrag = drag.current;

    if (!currentDrag || currentDrag.pointerId !== event.pointerId) {
      return;
    }
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    const finalOffset = Math.min(
      0,
      Math.max(
        -STOP_ACTION_WIDTH,
        currentDrag.baseOffset + event.clientX - currentDrag.startX,
      ),
    );
    const reveal = !canceled && finalOffset < -STOP_ACTION_WIDTH / 2;

    if (currentDrag.horizontal) {
      suppressNextClick.current = true;
      window.setTimeout(() => {
        suppressNextClick.current = false;
      }, 0);
    }
    drag.current = null;
    setIsDragging(false);
    setIsRevealed(reveal);
    setOffset(reveal ? -STOP_ACTION_WIDTH : 0);
  };

  return (
    <div className="relative overflow-hidden rounded-xl">
      <Button
        className="absolute inset-y-0 right-0 h-full w-[132px] rounded-none"
        isDisabled={!isRevealed || isStopping}
        variant="danger"
        onPress={onStopTracking}
      >
        {isStopping ? "Stopping…" : "Stop tracking"}
      </Button>
      <div
        className="swipe-order-card relative touch-pan-y"
        style={{
          transform: `translate3d(${offset}px, 0, 0)`,
          transition: isDragging ? "none" : "transform 220ms ease-out",
        }}
        onPointerCancel={(event) => settleSwipe(event, true)}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={settleSwipe}
      >
        <Card className="w-full">
          <Card.Header className="flex-row items-center gap-2 p-0">
            <NextLink
              className="flex min-w-0 flex-1 items-center justify-between gap-4 rounded-l-xl px-4 py-4 no-underline outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-inset"
              href={getTrackedOrderHref(order.trackingReference)}
              onClick={(event) => {
                if (suppressNextClick.current || isRevealed) {
                  event.preventDefault();
                  setIsRevealed(false);
                  setOffset(0);
                }
              }}
            >
              <Card.Title className="min-w-0 break-words text-lg">
                Order {order.label}
              </Card.Title>
              <Chip
                color={order.status === "READY" ? "success" : "default"}
                size="sm"
              >
                {orderStatusLabels[order.status]}
              </Chip>
            </NextLink>
            <Dropdown>
              <Dropdown.Trigger
                aria-label={`More actions for order ${order.label}`}
                className="mr-2 flex size-11 items-center justify-center rounded-full outline-none focus-visible:ring-2 focus-visible:ring-accent"
                isDisabled={isStopping}
              >
                <MoreIcon />
              </Dropdown.Trigger>
              <Dropdown.Popover placement="bottom end">
                <Dropdown.Menu
                  aria-label={`Actions for order ${order.label}`}
                  onAction={(key) => {
                    if (key === "stop-tracking") {
                      onStopTracking();
                    }
                  }}
                >
                  <Dropdown.Item
                    id="stop-tracking"
                    textValue="Stop tracking"
                    variant="danger"
                  >
                    Stop tracking
                  </Dropdown.Item>
                </Dropdown.Menu>
              </Dropdown.Popover>
            </Dropdown>
          </Card.Header>
        </Card>
      </div>
    </div>
  );
}

type HomeState = {
  lastDestination: StableCustomerDestination | null;
  orders: StoredTrackedOrder[];
};

export function CustomerHome({
  installationTrackingReference,
}: {
  installationTrackingReference: string | null;
}) {
  const router = useRouter();
  const { stopTrackingOrder } = useCustomerNotifications();
  const [homeState, setHomeState] = useState<HomeState | null>(null);
  const [removalError, setRemovalError] = useState<string | null>(null);
  const [stoppingReference, setStoppingReference] = useState<string | null>(
    null,
  );
  const hasResolvedInstalledLaunch = useRef(false);

  useEffect(() => {
    let active = true;

    void Promise.all([
      pruneTerminalTrackedOrders(),
      readLastStableDestination(),
    ]).then(([orders, lastDestination]) => {
      if (active) {
        setHomeState({ orders, lastDestination });
      }
    });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!homeState || hasResolvedInstalledLaunch.current) {
      return;
    }

    hasResolvedInstalledLaunch.current = true;

    if (isStandaloneDisplayMode()) {
      const installationOrder = consumeInstallationBootstrap(
        installationTrackingReference,
      );
      const launchHref = resolveInstalledLaunchHref({
        installationTrackingReference: installationOrder,
        lastDestination: homeState.lastDestination,
        orders: homeState.orders,
      });

      if (launchHref !== "/") {
        router.replace(launchHref);

        return;
      }
      if (installationTrackingReference) {
        router.replace("/");
      }
    }
    void rememberLastStableDestination({ kind: "home" });
  }, [homeState, installationTrackingReference, router]);

  const openScanner = () => {
    router.push("/scan");
  };

  const handleStopTracking = async (trackingReference: string) => {
    setRemovalError(null);
    setStoppingReference(trackingReference);
    const removed = await stopTrackingOrder(trackingReference);

    if (removed) {
      setHomeState((current) =>
        current
          ? {
              ...current,
              orders: current.orders.filter(
                (order) => order.trackingReference !== trackingReference,
              ),
            }
          : current,
      );
    } else {
      setRemovalError(
        "This order is still being tracked. Check your connection and try again.",
      );
    }
    setStoppingReference(null);
  };

  if (!homeState) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner aria-label="Loading your orders" />
      </div>
    );
  }

  if (homeState.orders.length === 0) {
    return (
      <section className="flex flex-col gap-6">
        <h1 className="text-3xl font-semibold tracking-tight">Your orders</h1>
        <div className="flex min-h-[52vh] flex-col items-center justify-center gap-4 text-center">
          <Button
            aria-label="Scan an order"
            className="h-auto min-h-32 w-44 flex-col gap-3 rounded-3xl py-6"
            variant="primary"
            onPress={openScanner}
          >
            <QrScannerIcon size={40} />
            <span className="text-base font-semibold">Scan an order</span>
          </Button>
          <p className="max-w-xs text-sm text-muted">
            Scan the QR code from the restaurant to start tracking.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-3xl font-semibold tracking-tight">Your orders</h1>
          <p className="mt-1 text-muted">
            Select an order for its latest status.
          </p>
        </div>
        <Button size="sm" variant="secondary" onPress={openScanner}>
          <QrScannerIcon size={18} />
          Scan
        </Button>
      </div>

      {removalError && (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Could not stop tracking</Alert.Title>
            <Alert.Description>{removalError}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <div aria-live="polite" className="flex flex-col gap-3">
        {homeState.orders.map((order) => (
          <OrderSummaryCard
            key={order.trackingReference}
            isStopping={stoppingReference === order.trackingReference}
            order={order}
            onStopTracking={() => {
              void handleStopTracking(order.trackingReference);
            }}
          />
        ))}
      </div>
    </section>
  );
}

"use client";

import type { PointerEvent as ReactPointerEvent } from "react";

import { Alert, Button, Dropdown, Spinner, Tooltip } from "@heroui/react";
import { Ellipsis, QrCode } from "lucide-react";
import NextLink from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { CustomerToolbar } from "@/components/customer-toolbar";
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
        if (event.currentTarget.hasPointerCapture(event.pointerId)) {
          event.currentTarget.releasePointerCapture(event.pointerId);
        }

        return;
      }
      currentDrag.horizontal = true;
      event.currentTarget.setPointerCapture(event.pointerId);
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
    const reveal =
      currentDrag.horizontal &&
      !canceled &&
      finalOffset < -STOP_ACTION_WIDTH / 2;

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
    <div className="relative overflow-hidden rounded-[var(--radius-medium)]">
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
        <div className="customer-order-entry flex w-full items-center gap-2">
          <NextLink
            className="flex min-w-0 flex-1 items-center justify-between gap-4 rounded-md px-3 py-5 no-underline outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-inset"
            href={getTrackedOrderHref(order.trackingReference)}
            onClick={(event) => {
              if (suppressNextClick.current || isRevealed) {
                event.preventDefault();
                setIsRevealed(false);
                setOffset(0);
              }
            }}
          >
            <div className="min-w-0">
              <p className="text-xs font-medium uppercase tracking-[0.1em] secondary-text">
                Order
              </p>
              <p className="mt-1 min-w-0 break-words text-xl font-semibold tracking-tight">
                {order.label}
              </p>
            </div>
            <span className="text-xs font-semibold uppercase tracking-[0.08em] text-accent">
              {orderStatusLabels[order.status]}
            </span>
          </NextLink>
          <Dropdown>
            <Tooltip delay={500}>
              <Tooltip.Trigger>
                <Dropdown.Trigger
                  aria-label={`More actions for order ${order.label}`}
                  className="flex size-11 items-center justify-center rounded-md outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-inset"
                  isDisabled={isStopping}
                >
                  <Ellipsis aria-hidden="true" size={20} />
                </Dropdown.Trigger>
              </Tooltip.Trigger>
              <Tooltip.Content>More actions</Tooltip.Content>
            </Tooltip>
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
        </div>
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
      <section className="flex min-h-[calc(100svh-3rem)] flex-col">
        <CustomerToolbar />
        <div className="flex flex-1 items-center justify-center">
          <Spinner aria-label="Loading your orders" />
        </div>
      </section>
    );
  }

  if (homeState.orders.length === 0) {
    return (
      <section className="relative min-h-[calc(100svh-3rem)]">
        <CustomerToolbar className="absolute right-0 top-0 z-10" />
        <div className="flex min-h-[calc(100svh-3rem)] items-center justify-center">
          <Button
            aria-label="Scan QR code"
            className="empty-scan-action"
            variant="tertiary"
            onPress={openScanner}
          >
            <span className="empty-scan-action-label">Scan QR code</span>
            <QrCode className="empty-scan-action-icon" size={128} />
          </Button>
        </div>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-8">
      <div className="flex flex-col-reverse gap-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="page-title">Your orders</h1>
        </div>
        <div className="flex items-center gap-1 self-end sm:self-auto">
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                aria-label="Scan another order"
                className="rounded-md"
                variant="tertiary"
                onPress={openScanner}
              >
                <QrCode size={20} />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>Scan another order</Tooltip.Content>
          </Tooltip>
          <CustomerToolbar />
        </div>
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

      <div aria-live="polite" className="flex flex-col gap-2">
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

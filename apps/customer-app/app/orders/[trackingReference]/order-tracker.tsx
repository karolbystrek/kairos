"use client";

import { Alert, Button, Chip, Spinner } from "@heroui/react";
import useSWR from "swr";

import { ApiError, getTrackedOrder, type OrderStatus } from "@/src/api/orders";

const statusLabels: Record<OrderStatus, string> = {
  CREATED: "Created",
  IN_PREPARATION: "In preparation",
  READY: "Ready for pickup",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

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

export function OrderTracker({
  trackingReference,
}: {
  trackingReference: string;
}) {
  const {
    data: order,
    error,
    isLoading,
    isValidating,
    mutate,
  } = useSWR(
    ["tracked-order", trackingReference] as const,
    ([, reference]) => getTrackedOrder(reference),
    {
      errorRetryCount: 3,
      shouldRetryOnError,
    },
  );

  if (isLoading && !order) {
    return <Spinner aria-label="Loading order" />;
  }

  if (!order) {
    return (
      <Alert status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Order unavailable</Alert.Title>
          <Alert.Description>
            {getTrackingErrorMessage(error)}
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }

  return (
    <section className="flex flex-col items-start gap-4">
      {error && (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Status may be out of date</Alert.Title>
            <Alert.Description>
              {getTrackingErrorMessage(error)} Showing the last known status.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}
      <div>
        <h1 className="text-3xl font-semibold">Order {order.label}</h1>
        <p className="text-muted">Current order status</p>
      </div>
      <Chip color={order.status === "READY" ? "success" : "default"} size="lg">
        {statusLabels[order.status]}
      </Chip>
      <p className="text-sm text-muted">
        Updated {new Date(order.updatedAt).toLocaleString()}
      </p>
      <Button
        isDisabled={isValidating}
        variant="secondary"
        onPress={() => {
          void mutate(undefined, { throwOnError: false });
        }}
      >
        {isValidating ? "Refreshing…" : "Refresh"}
      </Button>
    </section>
  );
}

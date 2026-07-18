"use client";

import { Alert, Button, Chip, Spinner } from "@heroui/react";
import { useCallback, useEffect, useState } from "react";

import {
  getTrackedOrder,
  type CustomerOrder,
  type OrderStatus,
} from "@/src/api/orders";

const statusLabels: Record<OrderStatus, string> = {
  CREATED: "Created",
  IN_PREPARATION: "In preparation",
  READY: "Ready for pickup",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

export function OrderTracker({
  trackingReference,
}: {
  trackingReference: string;
}) {
  const [order, setOrder] = useState<CustomerOrder>();
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string>();

  const loadOrder = useCallback(async () => {
    setIsLoading(true);
    setError(undefined);
    try {
      setOrder(await getTrackedOrder(trackingReference));
    } catch {
      setOrder(undefined);
      setError("This order could not be found.");
    } finally {
      setIsLoading(false);
    }
  }, [trackingReference]);

  useEffect(() => {
    void loadOrder();
  }, [loadOrder]);

  if (isLoading) {
    return <Spinner aria-label="Loading order" />;
  }

  if (error || !order) {
    return (
      <Alert status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Order unavailable</Alert.Title>
          <Alert.Description>{error}</Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }

  return (
    <section className="flex flex-col items-start gap-4">
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
      <Button variant="secondary" onPress={loadOrder}>
        Refresh
      </Button>
    </section>
  );
}

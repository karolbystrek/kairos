"use client";

import type { FormEvent } from "react";

import {
  Alert,
  Button,
  Chip,
  Input,
  Label,
  Link,
  Spinner,
  TextField,
} from "@heroui/react";
import Image from "next/image";
import QRCode from "qrcode";
import { useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";

import {
  ApiError,
  createOrder as createOrderRequest,
  listLocations,
  listOrders,
  updateOrderStatus,
  type OrderStatus,
  type StaffOrder,
} from "@/src/api/orders";

const customerAppUrl =
  process.env.NEXT_PUBLIC_CUSTOMER_APP_URL ?? "https://app.localhost";

const locationsKey = ["locations"] as const;
const ordersKey = (locationId: string) => ["orders", locationId] as const;

const statusLabels: Record<OrderStatus, string> = {
  CREATED: "Created",
  IN_PREPARATION: "In preparation",
  READY: "Ready",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

const nextStatuses: Partial<Record<OrderStatus, OrderStatus>> = {
  CREATED: "IN_PREPARATION",
  IN_PREPARATION: "READY",
  READY: "COMPLETED",
};

type OrderListKey = ReturnType<typeof ordersKey>;
type StatusMutationInput = {
  orderId: string;
  status: OrderStatus;
};

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }

  return error instanceof Error
    ? error.message
    : "An unexpected error occurred.";
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

function createOrderMutation(
  [, locationId]: OrderListKey,
  { arg: label }: { arg: string },
): Promise<StaffOrder> {
  return createOrderRequest(locationId, label);
}

function updateOrderMutation(
  _key: OrderListKey,
  { arg }: { arg: StatusMutationInput },
): Promise<StaffOrder> {
  return updateOrderStatus(arg.orderId, arg.status);
}

function OrderQrCode({ order }: { order: StaffOrder }) {
  const trackingUrl = `${customerAppUrl}/orders/${order.trackingReference}`;
  const {
    data: qrCode,
    error,
    isLoading,
  } = useSWR(
    ["order-qr", trackingUrl] as const,
    ([, url]) => QRCode.toDataURL(url, { margin: 1, width: 240 }),
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      shouldRetryOnError: false,
    },
  );

  return (
    <section className="flex flex-col items-start gap-4">
      <div>
        <h2 className="text-2xl font-semibold">Customer QR code</h2>
        <p className="text-muted">Order {order.label}</p>
      </div>
      {error ? (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>QR code unavailable</Alert.Title>
            <Alert.Description>
              The tracking link is available below, but its QR code could not be
              generated.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      ) : isLoading || !qrCode ? (
        <Spinner aria-label="Generating QR code" />
      ) : (
        <Image
          unoptimized
          alt={`Tracking QR code for order ${order.label}`}
          height={240}
          src={qrCode}
          width={240}
        />
      )}
      <Link href={trackingUrl} target="_blank">
        Open customer tracking page
      </Link>
    </section>
  );
}

export default function Home() {
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const [selectedOrderId, setSelectedOrderId] = useState<string>();
  const [label, setLabel] = useState("");

  const {
    data: locations = [],
    error: locationsError,
    isLoading: areLocationsLoading,
  } = useSWR(locationsKey, () => listLocations(), {
    errorRetryCount: 3,
    shouldRetryOnError,
  });

  const locationId = locations.some(
    (location) => location.id === selectedLocationId,
  )
    ? selectedLocationId
    : locations[0]?.id;
  const currentOrdersKey = locationId ? ordersKey(locationId) : null;

  const {
    data: orders = [],
    error: ordersError,
    isLoading: areOrdersLoading,
    isValidating: areOrdersValidating,
    mutate: mutateOrders,
  } = useSWR(
    currentOrdersKey,
    ([, currentLocationId]) => listOrders(currentLocationId),
    {
      errorRetryCount: 3,
      shouldRetryOnError,
    },
  );

  const {
    error: createOrderError,
    isMutating: isCreatingOrder,
    reset: resetCreateOrder,
    trigger: triggerCreateOrder,
  } = useSWRMutation(currentOrdersKey, createOrderMutation, {
    throwOnError: false,
  });

  const {
    error: updateOrderError,
    isMutating: isUpdatingOrder,
    reset: resetUpdateOrder,
    trigger: triggerUpdateOrder,
  } = useSWRMutation(currentOrdersKey, updateOrderMutation, {
    throwOnError: false,
  });

  const selectedOrder = orders.find((order) => order.id === selectedOrderId);
  const error =
    locationsError ?? ordersError ?? createOrderError ?? updateOrderError;

  async function createOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!locationId || !label.trim()) return;

    const order = await triggerCreateOrder(label.trim());

    if (!order) return;

    await mutateOrders(
      (current) => [
        order,
        ...(current ?? []).filter((item) => item.id !== order.id),
      ],
      { revalidate: false },
    );
    setSelectedOrderId(order.id);
    setLabel("");
    void mutateOrders(undefined, { throwOnError: false });
  }

  async function updateStatus(order: StaffOrder, status: OrderStatus) {
    const updated = await triggerUpdateOrder({ orderId: order.id, status });

    if (!updated) return;

    await mutateOrders(
      (current) =>
        (current ?? [updated]).map((item) =>
          item.id === updated.id ? updated : item,
        ),
      { revalidate: false },
    );
    void mutateOrders(undefined, { throwOnError: false });
  }

  function selectLocation(nextLocationId: string) {
    setSelectedLocationId(nextLocationId);
    setSelectedOrderId(undefined);
    resetCreateOrder();
    resetUpdateOrder();
  }

  return (
    <div className="flex flex-col gap-8">
      <header>
        <h1 className="text-3xl font-semibold">Kairos Staff Panel</h1>
        <p className="text-muted">Unauthenticated local development slice</p>
      </header>

      {error && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Request failed</Alert.Title>
            <Alert.Description>{getErrorMessage(error)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {areLocationsLoading ? (
        <Spinner aria-label="Loading locations" />
      ) : locations.length === 0 ? (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>No locations found</Alert.Title>
            <Alert.Description>
              Create a tenant and location in PostgreSQL, then refresh this
              page.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      ) : (
        <>
          <section className="flex flex-col gap-3">
            <h2 className="text-2xl font-semibold">Location</h2>
            <div className="flex flex-wrap gap-2">
              {locations.map((location) => (
                <Button
                  key={location.id}
                  variant={location.id === locationId ? "primary" : "secondary"}
                  onPress={() => selectLocation(location.id)}
                >
                  {location.name}
                </Button>
              ))}
            </div>
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-2xl font-semibold">Create order</h2>
            <form className="flex items-end gap-3" onSubmit={createOrder}>
              <TextField fullWidth isRequired>
                <Label>Order label</Label>
                <Input
                  maxLength={80}
                  placeholder="A-42"
                  value={label}
                  onChange={(event) => setLabel(event.target.value)}
                />
              </TextField>
              <Button
                isDisabled={isCreatingOrder || !label.trim()}
                type="submit"
                variant="primary"
              >
                {isCreatingOrder ? "Creating…" : "Create"}
              </Button>
            </form>
          </section>

          {selectedOrder && <OrderQrCode order={selectedOrder} />}

          <section className="flex flex-col gap-3">
            <div className="flex items-center gap-3">
              <h2 className="text-2xl font-semibold">Orders</h2>
              {areOrdersValidating && !areOrdersLoading && (
                <span className="text-sm text-muted">Refreshing…</span>
              )}
            </div>
            {areOrdersLoading ? (
              <Spinner aria-label="Loading orders" />
            ) : orders.length === 0 ? (
              <p className="text-muted">No orders at this location.</p>
            ) : (
              orders.map((order) => {
                const nextStatus = nextStatuses[order.status];
                const isTerminal =
                  order.status === "COMPLETED" || order.status === "CANCELED";

                return (
                  <article
                    key={order.id}
                    className="flex flex-wrap items-center justify-between gap-3 border-t border-separator py-4"
                  >
                    <div className="flex items-center gap-3">
                      <strong>{order.label}</strong>
                      <Chip>{statusLabels[order.status]}</Chip>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="secondary"
                        onPress={() => setSelectedOrderId(order.id)}
                      >
                        Show QR
                      </Button>
                      {nextStatus && (
                        <Button
                          isDisabled={isUpdatingOrder}
                          variant="primary"
                          onPress={() => updateStatus(order, nextStatus)}
                        >
                          Mark {statusLabels[nextStatus].toLowerCase()}
                        </Button>
                      )}
                      {!isTerminal && (
                        <Button
                          isDisabled={isUpdatingOrder}
                          variant="danger"
                          onPress={() => updateStatus(order, "CANCELED")}
                        >
                          Cancel
                        </Button>
                      )}
                    </div>
                  </article>
                );
              })
            )}
          </section>
        </>
      )}
    </div>
  );
}

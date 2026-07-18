"use client";

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
import { FormEvent, useCallback, useEffect, useState } from "react";
import QRCode from "qrcode";

import {
  ApiError,
  createOrder as createOrderRequest,
  listLocations,
  listOrders,
  updateOrderStatus,
  type Location,
  type OrderStatus,
  type StaffOrder,
} from "@/src/api/orders";

const customerAppUrl =
  process.env.NEXT_PUBLIC_CUSTOMER_APP_URL ?? "https://app.localhost";

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

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }

  return error instanceof Error
    ? error.message
    : "An unexpected error occurred.";
}

function OrderQrCode({ order }: { order: StaffOrder }) {
  const trackingUrl = `${customerAppUrl}/orders/${order.trackingReference}`;
  const [qrCode, setQrCode] = useState<string>();

  useEffect(() => {
    let active = true;

    QRCode.toDataURL(trackingUrl, { margin: 1, width: 240 }).then((dataUrl) => {
      if (active) setQrCode(dataUrl);
    });

    return () => {
      active = false;
    };
  }, [trackingUrl]);

  return (
    <section className="flex flex-col items-start gap-4">
      <div>
        <h2 className="text-2xl font-semibold">Customer QR code</h2>
        <p className="text-muted">Order {order.label}</p>
      </div>
      {qrCode ? (
        <Image
          unoptimized
          alt={`Tracking QR code for order ${order.label}`}
          height={240}
          src={qrCode}
          width={240}
        />
      ) : (
        <Spinner aria-label="Generating QR code" />
      )}
      <Link href={trackingUrl} target="_blank">
        Open customer tracking page
      </Link>
    </section>
  );
}

export default function Home() {
  const [locations, setLocations] = useState<Location[]>([]);
  const [locationId, setLocationId] = useState<string>();
  const [orders, setOrders] = useState<StaffOrder[]>([]);
  const [label, setLabel] = useState("");
  const [selectedOrder, setSelectedOrder] = useState<StaffOrder>();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string>();

  const loadLocations = useCallback(async () => {
    setIsLoading(true);
    setError(undefined);
    try {
      const result = await listLocations();

      setLocations(result);
      setLocationId((current) =>
        current && result.some((location) => location.id === current)
          ? current
          : result[0]?.id,
      );
    } catch (caught) {
      setError(getErrorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadOrders = useCallback(async () => {
    if (!locationId) {
      setOrders([]);

      return;
    }

    setError(undefined);
    try {
      setOrders(await listOrders(locationId));
    } catch (caught) {
      setError(getErrorMessage(caught));
    }
  }, [locationId]);

  useEffect(() => {
    void loadLocations();
  }, [loadLocations]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  async function createOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!locationId || !label.trim()) return;

    setIsSaving(true);
    setError(undefined);
    try {
      const order = await createOrderRequest(locationId, label.trim());

      setOrders((current) => [order, ...current]);
      setSelectedOrder(order);
      setLabel("");
    } catch (caught) {
      setError(getErrorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  }

  async function updateStatus(order: StaffOrder, status: OrderStatus) {
    setIsSaving(true);
    setError(undefined);
    try {
      const updated = await updateOrderStatus(order.id, status);

      setOrders((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      setSelectedOrder((current) =>
        current?.id === updated.id ? updated : current,
      );
    } catch (caught) {
      setError(getErrorMessage(caught));
    } finally {
      setIsSaving(false);
    }
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
            <Alert.Description>{error}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {isLoading ? (
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
                  onPress={() => setLocationId(location.id)}
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
                isDisabled={isSaving || !label.trim()}
                type="submit"
                variant="primary"
              >
                Create
              </Button>
            </form>
          </section>

          {selectedOrder && <OrderQrCode order={selectedOrder} />}

          <section className="flex flex-col gap-3">
            <h2 className="text-2xl font-semibold">Orders</h2>
            {orders.length === 0 && (
              <p className="text-muted">No orders at this location.</p>
            )}
            {orders.map((order) => {
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
                      onPress={() => setSelectedOrder(order)}
                    >
                      Show QR
                    </Button>
                    {nextStatus && (
                      <Button
                        isDisabled={isSaving}
                        variant="primary"
                        onPress={() => updateStatus(order, nextStatus)}
                      >
                        Mark {statusLabels[nextStatus].toLowerCase()}
                      </Button>
                    )}
                    {!isTerminal && (
                      <Button
                        isDisabled={isSaving}
                        variant="danger"
                        onPress={() => updateStatus(order, "CANCELED")}
                      >
                        Cancel
                      </Button>
                    )}
                  </div>
                </article>
              );
            })}
          </section>
        </>
      )}
    </div>
  );
}

import {
  Alert,
  Button,
  Chip,
  Input,
  Label,
  Radio,
  RadioGroup,
  Spinner,
  TextField,
} from "@heroui/react";
import Image from "next/image";
import QRCode from "qrcode";
import { useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";

import { ApiError } from "@/src/api/api-fetch";
import { staffCachePrefix, staffLocationsKey } from "@/src/api/cache-keys";
import { listLocations } from "@/src/api/locations";
import {
  createOrder as createOrderRequest,
  createOrderInputSchema,
  listOrders,
  updateOrderStatus,
  type OrderStatus,
  type StaffOrder,
  type CreateOrderInput,
} from "@/src/api/orders";

const customerAppUrl =
  process.env.NEXT_PUBLIC_CUSTOMER_APP_URL ?? "https://app.localhost";
const tenantOrderScope = "tenant";

const ordersKey = (accountId: string, scope: string) =>
  [staffCachePrefix, accountId, "orders", scope] as const;

const statusLabels: Record<OrderStatus, string> = {
  IN_PREPARATION: "In preparation",
  READY: "Ready",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

const nextStatuses: Partial<Record<OrderStatus, OrderStatus>> = {
  IN_PREPARATION: "READY",
  READY: "COMPLETED",
};

type OrderListKey = ReturnType<typeof ordersKey>;
type CreateMutationInput = {
  locationId: string;
  input: CreateOrderInput;
};
type StatusMutationInput = {
  orderId: string;
  status: OrderStatus;
};

function getErrorMessage(error: unknown): string {
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
  _key: OrderListKey,
  { arg }: { arg: CreateMutationInput },
): Promise<StaffOrder> {
  return createOrderRequest(arg.locationId, arg.input);
}

function updateOrderMutation(
  _key: OrderListKey,
  { arg }: { arg: StatusMutationInput },
): Promise<StaffOrder> {
  return updateOrderStatus(arg.orderId, arg.status);
}

function OrderQrCode({
  accountId,
  order,
}: {
  accountId: string;
  order: StaffOrder;
}) {
  const trackingUrl = `${customerAppUrl}/orders/${order.trackingReference}`;
  const {
    data: qrCode,
    error,
    isLoading,
  } = useSWR(
    [staffCachePrefix, accountId, "order-qr", trackingUrl] as const,
    ([, , , url]) => QRCode.toDataURL(url, { margin: 1, width: 240 }),
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
              The customer QR code could not be generated. Try showing it again.
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
    </section>
  );
}

export function OrderManagement({
  accountId,
  canViewTenantOrders,
}: {
  accountId: string;
  canViewTenantOrders: boolean;
}) {
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const [selectedOrderId, setSelectedOrderId] = useState<string>();
  const [labelMode, setLabelMode] = useState<"AUTO" | "CUSTOM">("AUTO");
  const [customLabel, setCustomLabel] = useState("");
  const [customLabelError, setCustomLabelError] = useState<string>();

  const {
    data: locations = [],
    error: locationsError,
    isLoading: areLocationsLoading,
  } = useSWR(staffLocationsKey(accountId), () => listLocations(), {
    errorRetryCount: 3,
    shouldRetryOnError,
  });

  const locationId = locations.some(
    (location) => location.id === selectedLocationId,
  )
    ? selectedLocationId
    : canViewTenantOrders
      ? undefined
      : locations[0]?.id;
  const currentOrdersKey =
    locations.length > 0
      ? ordersKey(accountId, locationId ?? tenantOrderScope)
      : null;

  const {
    data: orders = [],
    error: ordersError,
    isLoading: areOrdersLoading,
    isValidating: areOrdersValidating,
    mutate: mutateOrders,
  } = useSWR(
    currentOrdersKey,
    ([, , , scope]) =>
      listOrders(scope === tenantOrderScope ? undefined : scope),
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

  async function createOrder() {
    if (!locationId) return;

    const input =
      labelMode === "AUTO"
        ? ({ mode: "AUTO" } as const)
        : ({ mode: "CUSTOM", label: customLabel } as const);
    const validation = createOrderInputSchema.safeParse(input);

    if (!validation.success) {
      setCustomLabelError(validation.error.issues[0]?.message);

      return;
    }

    setCustomLabelError(undefined);
    const order = await triggerCreateOrder({
      locationId,
      input: validation.data,
    });

    if (!order) return;

    await mutateOrders(
      (current) => [
        order,
        ...(current ?? []).filter((item) => item.id !== order.id),
      ],
      { revalidate: false },
    );
    setSelectedOrderId(order.id);
    void mutateOrders(undefined, { throwOnError: false });
  }

  async function updateStatus(order: StaffOrder, status: OrderStatus) {
    const updated = await triggerUpdateOrder({ orderId: order.id, status });

    if (!updated) return;

    const isTerminal =
      updated.status === "COMPLETED" || updated.status === "CANCELED";

    await mutateOrders(
      (current) =>
        isTerminal
          ? (current ?? []).filter((item) => item.id !== updated.id)
          : (current ?? [updated]).map((item) =>
              item.id === updated.id ? updated : item,
            ),
      { revalidate: false },
    );
    if (isTerminal && selectedOrderId === updated.id) {
      setSelectedOrderId(undefined);
    }
    void mutateOrders(undefined, { throwOnError: false });
  }

  function selectLocation(nextLocationId?: string) {
    setSelectedLocationId(nextLocationId);
    setSelectedOrderId(undefined);
    resetCreateOrder();
    resetUpdateOrder();
  }

  return (
    <div className="flex flex-col gap-8">
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
              Ask a tenant administrator to configure an accessible location.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      ) : (
        <>
          <section className="flex flex-col gap-3">
            <h2 className="text-2xl font-semibold">Location</h2>
            <div className="flex flex-wrap gap-2">
              {canViewTenantOrders && (
                <Button
                  variant={locationId === undefined ? "primary" : "secondary"}
                  onPress={() => selectLocation()}
                >
                  All locations
                </Button>
              )}
              {locations.map((location) => (
                <Button
                  key={location.id}
                  variant={location.id === locationId ? "primary" : "secondary"}
                  onPress={() => selectLocation(location.id)}
                >
                  {location.id}
                </Button>
              ))}
            </div>
          </section>

          {locationId ? (
            <section className="flex flex-col gap-3">
              <h2 className="text-2xl font-semibold">Create order</h2>
              <RadioGroup
                isDisabled={isCreatingOrder}
                name="order-label-mode"
                orientation="horizontal"
                value={labelMode}
                onChange={(value) => {
                  setLabelMode(value === "CUSTOM" ? "CUSTOM" : "AUTO");
                  setCustomLabelError(undefined);
                }}
              >
                <Label>Label</Label>
                <Radio value="AUTO">
                  <Radio.Content>
                    <Radio.Control>
                      <Radio.Indicator />
                    </Radio.Control>
                    Auto
                  </Radio.Content>
                </Radio>
                <Radio value="CUSTOM">
                  <Radio.Content>
                    <Radio.Control>
                      <Radio.Indicator />
                    </Radio.Control>
                    Custom
                  </Radio.Content>
                </Radio>
              </RadioGroup>
              {labelMode === "CUSTOM" && (
                <TextField
                  fullWidth
                  className="max-w-sm"
                  isDisabled={isCreatingOrder}
                  isInvalid={Boolean(customLabelError)}
                  maxLength={64}
                  name="custom-order-label"
                  value={customLabel}
                  onChange={(value) => {
                    setCustomLabel(value);
                    setCustomLabelError(undefined);
                  }}
                >
                  <Label>Custom label</Label>
                  <Input placeholder="Table 4" />
                </TextField>
              )}
              {customLabelError && (
                <p className="text-sm text-danger">{customLabelError}</p>
              )}
              <Button
                className="self-start"
                isDisabled={isCreatingOrder}
                variant="primary"
                onPress={createOrder}
              >
                {isCreatingOrder ? "Creating…" : "Create"}
              </Button>
            </section>
          ) : (
            <section className="flex flex-col gap-2">
              <h2 className="text-2xl font-semibold">Create order</h2>
              <p className="text-muted">
                Select a location before creating an order.
              </p>
            </section>
          )}

          {selectedOrder && (
            <OrderQrCode accountId={accountId} order={selectedOrder} />
          )}

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
              <p className="text-muted">
                {locationId
                  ? "No orders at this location."
                  : "No orders across this tenant."}
              </p>
            ) : (
              orders.map((order) => {
                const nextStatus = nextStatuses[order.status];

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
                      <Button
                        isDisabled={isUpdatingOrder}
                        variant="danger"
                        onPress={() => updateStatus(order, "CANCELED")}
                      >
                        Cancel
                      </Button>
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

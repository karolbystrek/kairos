import type { FormEvent } from "react";

import { Alert, Button, ListBox, Spinner, Tooltip } from "@heroui/react";
import {
  ArrowRight as ArrowRightIcon,
  Clock3 as ClockIcon,
  Plus as PlusIcon,
  X as CloseIcon,
} from "lucide-react";
import Image from "next/image";
import QRCode from "qrcode";
import { useEffect, useMemo, useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";

import { PanelPopup } from "@/components/panel-popup";
import { FormSelect, FormTextField } from "@/components/form-controls";
import { PanelCard } from "@/components/panel-card";
import { LocationCreationModal } from "@/components/location-creation-modal";
import { ApiError } from "@/src/api/api-fetch";
import { staffCachePrefix, staffLocationsKey } from "@/src/api/cache-keys";
import { listLocations, type Location } from "@/src/api/locations";
import {
  createOrder as createOrderRequest,
  createOrderInputSchema,
  listOrders,
  updateOrderStatus,
  type CreateOrderInput,
  type OrderStatus,
  type StaffOrder,
} from "@/src/api/orders";
import { customerAppUrl } from "@/src/config/public-environment";

const tenantOrderScope = "tenant";

const ordersKey = (accountId: string, scope: string) =>
  [staffCachePrefix, accountId, "orders", scope] as const;

const laneDetails = {
  IN_PREPARATION: {
    label: "W przygotowaniu",
  },
  READY: {
    label: "Gotowe",
  },
} as const;

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
  if (error instanceof ApiError) {
    switch (error.status) {
      case 403:
        return "Nie masz uprawnień do zarządzania tymi zamówieniami.";
      case 404:
        return "Wybrane zamówienie lub lokal nie jest już dostępny.";
      case 409:
        return "Zamówienie zostało zmienione. Sprawdź jego obecny status i spróbuj ponownie.";
      default:
        return error.message;
    }
  }

  return "Nie udało się wczytać lub zaktualizować zamówień. Sprawdź połączenie i spróbuj ponownie.";
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

function elapsedTime(createdAt: string, now: number): string {
  const minutes = Math.max(
    0,
    Math.floor((now - new Date(createdAt).getTime()) / 60_000),
  );

  if (minutes < 1) return "Przed chwilą";
  if (minutes < 60) return `${minutes.toLocaleString("pl-PL")} min`;

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;

  return remainingMinutes > 0
    ? `${hours.toLocaleString("pl-PL")} godz. ${remainingMinutes.toLocaleString("pl-PL")} min`
    : `${hours.toLocaleString("pl-PL")} godz.`;
}

function useMinuteClock() {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 60_000);

    return () => window.clearInterval(interval);
  }, []);

  return now;
}

function LocationSelect({
  isDisabled = false,
  label,
  locations,
  onChange,
  selectedId,
  showAll,
}: {
  isDisabled?: boolean;
  label: string;
  locations: Location[];
  onChange: (locationId?: string) => void;
  selectedId?: string;
  showAll: boolean;
}) {
  const items = showAll
    ? [{ id: "all", name: "Wszystkie lokale" }, ...locations]
    : locations;

  return (
    <FormSelect
      className="w-full sm:max-w-xs"
      isDisabled={isDisabled}
      label={label}
      selectedKey={selectedId ?? "all"}
      onSelectionChange={(key) =>
        onChange(key === "all" || key === null ? undefined : String(key))
      }
    >
      <ListBox items={items}>
        {(item) => (
          <ListBox.Item id={item.id} textValue={item.name}>
            {item.name}
          </ListBox.Item>
        )}
      </ListBox>
    </FormSelect>
  );
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
    ([, , , url]) => QRCode.toDataURL(url, { margin: 2, width: 640 }),
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      shouldRetryOnError: false,
    },
  );

  if (error) {
    return (
      <Alert status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Kod QR jest niedostępny</Alert.Title>
          <Alert.Description>
            Nie udało się wygenerować kodu QR dla klienta.
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }

  if (isLoading || !qrCode) {
    return (
      <div className="flex min-h-72 items-center justify-center">
        <Spinner aria-label="Generowanie kodu QR" />
      </div>
    );
  }

  return (
    <div className="flex w-full justify-center">
      <Image
        unoptimized
        alt={`Kod QR do śledzenia zamówienia ${order.label}`}
        className="h-auto w-full max-w-[30rem]"
        height={640}
        src={qrCode}
        width={640}
      />
    </div>
  );
}

function OrderCard({
  isUpdating,
  locationName,
  now,
  onCancel,
  onShowQr,
  onTransition,
  order,
  showLocation,
}: {
  isUpdating: boolean;
  locationName?: string;
  now: number;
  onCancel: (order: StaffOrder) => void;
  onShowQr: (order: StaffOrder) => void;
  onTransition: (order: StaffOrder, status: OrderStatus) => void;
  order: StaffOrder;
  showLocation: boolean;
}) {
  const nextStatus = nextStatuses[order.status];
  const nextStatusLabel = nextStatus === "READY" ? "Gotowe" : "Zakończ";
  const nextStatusAccessibilityLabel =
    nextStatus === "READY"
      ? `Oznacz zamówienie ${order.label} jako gotowe`
      : `Zakończ zamówienie ${order.label}`;

  return (
    <PanelCard
      accessibilityLabel={`Pokaż kod QR zamówienia ${order.label}`}
      metadata={
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 secondary-text">
          {showLocation && locationName && (
            <span className="break-words">{locationName}</span>
          )}
          <span className="flex shrink-0 items-center gap-1.5">
            <ClockIcon size={15} />
            {elapsedTime(order.createdAt, now)}
          </span>
        </div>
      }
      title={order.label}
      trailing={
        <>
          {nextStatus && (
            <Button
              aria-label={nextStatusAccessibilityLabel}
              isDisabled={isUpdating}
              size="lg"
              variant="primary"
              onPress={() => onTransition(order, nextStatus)}
            >
              {nextStatusLabel}
              <ArrowRightIcon size={18} />
            </Button>
          )}
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                aria-label={`Anuluj zamówienie ${order.label}`}
                className="rounded-md"
                isDisabled={isUpdating}
                size="lg"
                variant="danger"
                onPress={() => onCancel(order)}
              >
                <CloseIcon size={20} />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>Anuluj zamówienie</Tooltip.Content>
          </Tooltip>
        </>
      }
      onPress={() => onShowQr(order)}
    />
  );
}

export function OrderManagement({
  accountId,
  canManageLocations,
  canViewTenantOrders,
  onRequestedLocationApplied,
  requestedLocationId,
}: {
  accountId: string;
  canManageLocations: boolean;
  canViewTenantOrders: boolean;
  onRequestedLocationApplied?: () => void;
  requestedLocationId?: string;
}) {
  const now = useMinuteClock();
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const [createLocationId, setCreateLocationId] = useState<string>();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isCreateLocationOpen, setIsCreateLocationOpen] = useState(false);
  const [qrOrder, setQrOrder] = useState<StaffOrder>();
  const [cancelOrder, setCancelOrder] = useState<StaffOrder>();
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

  const enabledLocations = locations.filter(
    (location) => location.status === "ENABLED",
  );

  const effectiveSelectedLocationId = requestedLocationId ?? selectedLocationId;
  const locationId = enabledLocations.some(
    (location) => location.id === effectiveSelectedLocationId,
  )
    ? effectiveSelectedLocationId
    : canViewTenantOrders
      ? undefined
      : enabledLocations[0]?.id;
  const currentOrdersKey =
    enabledLocations.length > 0
      ? ordersKey(accountId, locationId ?? tenantOrderScope)
      : null;

  const {
    data: orders = [],
    error: ordersError,
    isLoading: areOrdersLoading,
    mutate: mutateOrders,
  } = useSWR(
    currentOrdersKey,
    ([, , , scope]) =>
      listOrders(scope === tenantOrderScope ? undefined : scope),
    { errorRetryCount: 3, shouldRetryOnError },
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

  const ordersByStatus = useMemo(
    () => ({
      IN_PREPARATION: orders.filter(
        (order) => order.status === "IN_PREPARATION",
      ),
      READY: orders.filter((order) => order.status === "READY"),
    }),
    [orders],
  );
  const locationNames = useMemo(
    () => new Map(locations.map((location) => [location.id, location.name])),
    [locations],
  );
  const error =
    locationsError ?? ordersError ?? createOrderError ?? updateOrderError;

  function openCreate() {
    if (isCreatingOrder) {
      setIsCreateOpen(true);

      return;
    }
    resetCreateOrder();
    setCreateLocationId(locationId ?? enabledLocations[0]?.id);
    setIsCreateOpen(true);
  }

  async function createOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!createLocationId || isCreatingOrder) return;

    const input = customLabel.trim()
      ? ({ mode: "CUSTOM", label: customLabel } as const)
      : ({ mode: "AUTO" } as const);
    const validation = createOrderInputSchema.safeParse(input);

    if (!validation.success) {
      setCustomLabelError(validation.error.issues[0]?.message);

      return;
    }

    setCustomLabelError(undefined);
    const order = await triggerCreateOrder({
      locationId: createLocationId,
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
    setCustomLabel("");
    setIsCreateOpen(false);
    setQrOrder(order);
    void mutateOrders(undefined, { throwOnError: false });
  }

  async function updateStatus(
    order: StaffOrder,
    status: OrderStatus,
  ): Promise<boolean> {
    if (isUpdatingOrder) return false;

    resetUpdateOrder();
    const updated = await triggerUpdateOrder({ orderId: order.id, status });

    if (!updated) return false;

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
    if (isTerminal && qrOrder?.id === updated.id) setQrOrder(undefined);
    void mutateOrders(undefined, { throwOnError: false });

    return true;
  }

  function selectLocation(nextLocationId?: string) {
    if (isCreatingOrder || isUpdatingOrder) return;
    setSelectedLocationId(nextLocationId);
    onRequestedLocationApplied?.();
    resetCreateOrder();
    resetUpdateOrder();
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="page-title">Zamówienia</h1>
        {enabledLocations.length > 0 && (
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                aria-label="Nowe zamówienie"
                className="rounded-md"
                size="lg"
                onPress={openCreate}
              >
                <PlusIcon size={20} />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>Nowe zamówienie</Tooltip.Content>
          </Tooltip>
        )}
      </div>

      {error && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wykonać operacji</Alert.Title>
            <Alert.Description>{getErrorMessage(error)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {areLocationsLoading ? (
        <Spinner aria-label="Wczytywanie lokali" />
      ) : enabledLocations.length === 0 ? (
        <div className="flex flex-col items-start gap-4 py-12">
          <div>
            <h2 className="section-title">Brak lokalu</h2>
            <p className="mt-2 secondary-text">
              Utwórz lokal, aby zarządzać zamówieniami.
            </p>
          </div>
          {canManageLocations && (
            <Button onPress={() => setIsCreateLocationOpen(true)}>
              <PlusIcon size={18} />
              Utwórz lokal
            </Button>
          )}
        </div>
      ) : (
        <>
          {canViewTenantOrders && (
            <LocationSelect
              showAll
              isDisabled={isCreatingOrder || isUpdatingOrder}
              label="Lokal kolejki"
              locations={enabledLocations}
              selectedId={locationId}
              onChange={selectLocation}
            />
          )}

          {areOrdersLoading ? (
            <div className="flex min-h-80 items-center justify-center">
              <Spinner aria-label="Wczytywanie zamówień" />
            </div>
          ) : (
            <div className="grid gap-10 md:grid-cols-2 md:gap-0 md:divide-x md:divide-separator">
              {(["IN_PREPARATION", "READY"] as const).map((status) => {
                const lane = laneDetails[status];
                const laneOrders = ordersByStatus[status];

                return (
                  <section
                    key={status}
                    className="border-t border-separator pt-5 md:border-t-0 md:px-6 md:first:pl-0 md:last:pr-0"
                  >
                    <header className="flex items-start justify-between gap-3 pb-3">
                      <div>
                        <h2 className="section-title">{lane.label}</h2>
                      </div>
                      <span className="text-sm tabular-nums secondary-text">
                        {laneOrders.length.toLocaleString("pl-PL")}
                      </span>
                    </header>
                    {laneOrders.length === 0 ? (
                      <p className="py-10 text-sm secondary-text">
                        {status === "READY"
                          ? "Brak zamówień do odbioru"
                          : "Brak zamówień w przygotowaniu"}
                      </p>
                    ) : (
                      <div>
                        {laneOrders.map((order) => (
                          <OrderCard
                            key={order.id}
                            isUpdating={isUpdatingOrder}
                            locationName={locationNames.get(order.locationId)}
                            now={now}
                            order={order}
                            showLocation={locationId === undefined}
                            onCancel={(selectedOrder) => {
                              if (isUpdatingOrder) return;
                              resetUpdateOrder();
                              setCancelOrder(selectedOrder);
                            }}
                            onShowQr={setQrOrder}
                            onTransition={(selectedOrder, nextStatus) => {
                              void updateStatus(selectedOrder, nextStatus);
                            }}
                          />
                        ))}
                      </div>
                    )}
                  </section>
                );
              })}
            </div>
          )}
        </>
      )}

      <PanelPopup
        isOpen={isCreateOpen}
        size="lg"
        onOpenChange={setIsCreateOpen}
      >
        <PanelPopup.Header>
          <PanelPopup.Heading>Nowe zamówienie</PanelPopup.Heading>
        </PanelPopup.Header>
        <form onSubmit={createOrder}>
          <PanelPopup.Body className="flex flex-col gap-4">
            {createOrderError && (
              <Alert status="danger">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title>Nie udało się utworzyć zamówienia</Alert.Title>
                  <Alert.Description>
                    {getErrorMessage(createOrderError)}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}
            {canViewTenantOrders && (
              <LocationSelect
                label="Lokal"
                locations={enabledLocations}
                selectedId={createLocationId}
                showAll={false}
                onChange={setCreateLocationId}
              />
            )}
            <FormTextField
              fullWidth
              isDisabled={isCreatingOrder}
              isInvalid={Boolean(customLabelError)}
              label="Etykieta zamówienia (opcjonalnie)"
              maxLength={32}
              name="custom-order-label"
              placeholder="Etykieta zamówienia (puste pole nadaje ją automatycznie)"
              value={customLabel}
              onChange={(value) => {
                setCustomLabel(value);
                setCustomLabelError(undefined);
              }}
            />
            {customLabelError && (
              <p className="text-sm text-danger">{customLabelError}</p>
            )}
          </PanelPopup.Body>
          <PanelPopup.Footer>
            <Button slot="close" variant="tertiary">
              Anuluj
            </Button>
            <Button isPending={isCreatingOrder} type="submit">
              <PlusIcon size={18} />
              {isCreatingOrder ? "Tworzenie…" : "Utwórz"}
            </Button>
          </PanelPopup.Footer>
        </form>
      </PanelPopup>

      {canManageLocations && (
        <LocationCreationModal
          accountId={accountId}
          isOpen={isCreateLocationOpen}
          onCreated={(location) => {
            setSelectedLocationId(location.id);
            onRequestedLocationApplied?.();
          }}
          onOpenChange={setIsCreateLocationOpen}
        />
      )}

      <PanelPopup
        isOpen={Boolean(qrOrder)}
        size="lg"
        onOpenChange={(open) => {
          if (!open) setQrOrder(undefined);
        }}
      >
        <PanelPopup.Header className="justify-center px-16 pb-2">
          <PanelPopup.Heading className="text-center text-lg font-medium tracking-normal secondary-text">
            {qrOrder?.label}
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex items-center justify-center px-6 pb-8 pt-2 sm:px-10">
          {qrOrder && <OrderQrCode accountId={accountId} order={qrOrder} />}
        </PanelPopup.Body>
      </PanelPopup>

      <PanelPopup
        className="sm:max-w-[420px]"
        isOpen={Boolean(cancelOrder)}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) setCancelOrder(undefined);
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="danger" />
          <PanelPopup.Heading>
            Anuluj zamówienie {cancelOrder?.label}?
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body>
          <p>
            Klient zobaczy, że zamówienie zostało anulowane. Tej operacji nie
            można cofnąć.
          </p>
          {updateOrderError && (
            <Alert className="mt-4" status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>Nie udało się anulować zamówienia</Alert.Title>
                <Alert.Description>
                  {getErrorMessage(updateOrderError)}
                </Alert.Description>
              </Alert.Content>
            </Alert>
          )}
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button slot="close" variant="tertiary">
            Zachowaj zamówienie
          </Button>
          <Button
            isPending={isUpdatingOrder}
            variant="danger"
            onPress={() => {
              if (!cancelOrder) return;
              void updateStatus(cancelOrder, "CANCELED").then((didUpdate) => {
                if (didUpdate) setCancelOrder(undefined);
              });
            }}
          >
            Anuluj zamówienie
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>
    </div>
  );
}

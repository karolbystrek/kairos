import type { FormEvent } from "react";

import { Alert, Button, ListBox, Spinner, Tooltip, toast } from "@heroui/react";
import {
  ArrowCounterClockwiseIcon,
  ArrowLeftIcon,
  ArrowRightIcon,
  PlusIcon,
  XIcon as CloseIcon,
} from "@phosphor-icons/react";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";

import { LoadingIndicator } from "@/components/loading-indicator";
import { PanelPopup } from "@/components/panel-popup";
import { FormSelect, FormTextField } from "@/components/form-controls";
import { PanelCard } from "@/components/panel-card";
import { LocationCreationModal } from "@/components/location-creation-modal";
import {
  OrderItemPicker,
  type ItemQuantities,
} from "@/components/order-item-picker";
import { ApiError } from "@/src/api/api-fetch";
import { staffCachePrefix, staffLocationMenuKey } from "@/src/api/cache-keys";
import { useStaffLocations, type Location } from "@/src/api/locations";
import { getLocationMenu } from "@/src/api/menus";
import {
  createOrder as createOrderRequest,
  createOrderInputSchema,
  describeOrderItems,
  listOrders,
  maximumOrderLabelLength,
  subscribeToOrderQueue,
  updateOrderStatus,
  type CreateOrderInput,
  type OrderStatus,
  type StaffOrder,
} from "@/src/api/orders";
import { customerAppUrl } from "@/src/config/public-environment";
import { recordPanelAction } from "@/src/api/usage-analytics";
import { errorMessage } from "@/src/messages";
import { orderQrCode } from "@/src/order-qr-code";
import { toastError } from "@/src/toast";

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

const statusErrorMessages: Partial<Record<number, string>> = {
  403: "Nie masz uprawnień do zarządzania tymi zamówieniami.",
  404: "Wybrane zamówienie lub lokal nie jest już dostępny.",
  409: "Zamówienie zostało zmienione. Sprawdź jego obecny status i spróbuj ponownie.",
};

function getErrorMessage(error: unknown): string {
  return (
    (error instanceof ApiError && statusErrorMessages[error.status]) ||
    errorMessage(
      error,
      "Nie udało się wczytać lub zaktualizować zamówień. Sprawdź połączenie i spróbuj ponownie.",
    )
  );
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

export function useMinuteClock() {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 60_000);

    return () => window.clearInterval(interval);
  }, []);

  return now;
}

// Owns the minute tick so only this text re-renders, not the whole queue.
function ElapsedTime({ createdAt }: { createdAt: string }) {
  return <>{elapsedTime(createdAt, useMinuteClock())}</>;
}

export function LocationSelect({
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
    ([, , , url]) => orderQrCode(url),
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
        alt={`Kod QR do śledzenia zamówienia #${order.number}`}
        className="h-auto w-full max-w-[min(30rem,60dvh)]"
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
  onCancel,
  onShowDetails,
  onTransition,
  order,
  showLocation,
}: {
  isUpdating: boolean;
  locationName?: string;
  onCancel: (order: StaffOrder) => void;
  onShowDetails: (order: StaffOrder) => void;
  onTransition: (order: StaffOrder, status: OrderStatus) => void;
  order: StaffOrder;
  showLocation: boolean;
}) {
  const nextStatus = nextStatuses[order.status];
  const nextStatusLabel = nextStatus === "READY" ? "Gotowe" : "Zakończ";
  const nextStatusAccessibilityLabel =
    nextStatus === "READY"
      ? `Oznacz zamówienie #${order.number} jako gotowe`
      : `Zakończ zamówienie #${order.number}`;

  return (
    <PanelCard
      accessibilityLabel={`Pokaż szczegóły zamówienia #${order.number}`}
      metadata={
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 secondary-text">
          {showLocation && locationName && (
            <span className="break-words">{locationName}</span>
          )}
          <span className="flex shrink-0 items-center gap-1.5">
            <ElapsedTime createdAt={order.createdAt} />
          </span>
        </div>
      }
      title={
        <>
          <span className="order-number">#{order.number}</span>
          {order.label && (
            <span className="ml-2 text-sm font-normal secondary-text">
              {order.label}
            </span>
          )}
        </>
      }
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
                aria-label={`Anuluj zamówienie #${order.number}`}
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
      onPress={() => onShowDetails(order)}
    />
  );
}

export function OrderManagement({
  accountId,
  canManageLocations,
  canViewTenantOrders,
  onSelectLocation,
  selectedLocationId,
}: {
  accountId: string;
  canManageLocations: boolean;
  canViewTenantOrders: boolean;
  onSelectLocation: (locationId?: string) => void;
  selectedLocationId?: string;
}) {
  const [createLocationId, setCreateLocationId] = useState<string>();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createStep, setCreateStep] = useState<"items" | "label">("items");
  const [isCreateLocationOpen, setIsCreateLocationOpen] = useState(false);
  const [detailsOrder, setDetailsOrder] = useState<StaffOrder>();
  // Kept separate so the closing popup keeps its content and width while it animates out.
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const stepChangedAt = useRef(-Infinity);
  const [cancelOrder, setCancelOrder] = useState<StaffOrder>();
  const [label, setLabel] = useState("");
  const [labelError, setLabelError] = useState<string>();
  const [itemQuantities, setItemQuantities] = useState<ItemQuantities>({});

  const {
    data: locations = [],
    enabledLocations,
    error: locationsError,
    isLoading: areLocationsLoading,
  } = useStaffLocations(accountId);

  const locationId = enabledLocations.some(
    (location) => location.id === selectedLocationId,
  )
    ? selectedLocationId
    : canViewTenantOrders
      ? undefined
      : enabledLocations[0]?.id;
  // The orders tab panel unmounts when hidden, so each visit is a fresh mount.
  const queueWasOpened = useRef(false);

  useEffect(() => {
    if (areLocationsLoading || locationsError || queueWasOpened.current) return;
    queueWasOpened.current = true;
    recordPanelAction("queue_opened", locationId);
  }, [areLocationsLoading, locationsError, locationId]);

  const currentOrdersKey =
    enabledLocations.length > 0
      ? ordersKey(accountId, locationId ?? tenantOrderScope)
      : null;
  const queueScope = currentOrdersKey
    ? `${accountId}:${locationId ?? tenantOrderScope}`
    : null;
  const [connectedQueueScope, setConnectedQueueScope] = useState<string>();

  const {
    data: orders = [],
    error: ordersError,
    isLoading: areOrdersLoading,
    mutate: mutateOrders,
  } = useSWR(
    currentOrdersKey,
    ([, , , scope]) =>
      listOrders(scope === tenantOrderScope ? undefined : scope),
    {
      refreshInterval: connectedQueueScope !== queueScope ? 15_000 : 0,
      // The stream already delivers every change while connected.
      revalidateOnFocus: connectedQueueScope !== queueScope,
    },
  );

  useEffect(() => {
    if (!queueScope) return;
    let disposed = false;
    const disconnect = () => {
      if (disposed) return;
      setConnectedQueueScope(undefined);
      void mutateOrders().catch(() => undefined);
    };
    const close = subscribeToOrderQueue(
      locationId,
      (snapshot) => {
        if (disposed) return;
        setConnectedQueueScope(queueScope);
        // SWR discards REST requests started before this snapshot arrived.
        void mutateOrders(snapshot, { revalidate: false });
      },
      disconnect,
    );

    return () => {
      disposed = true;
      close();
    };
  }, [queueScope, locationId, mutateOrders]);

  const {
    error: createOrderError,
    isMutating: isCreatingOrder,
    reset: resetCreateOrder,
    trigger: triggerCreateOrder,
  } = useSWRMutation(
    currentOrdersKey,
    (_, { arg }: { arg: { locationId: string; input: CreateOrderInput } }) =>
      createOrderRequest(arg.locationId, arg.input),
    { throwOnError: false },
  );
  const {
    error: updateOrderError,
    isMutating: isUpdatingOrder,
    reset: resetUpdateOrder,
    trigger: triggerUpdateOrder,
  } = useSWRMutation(
    currentOrdersKey,
    (_, { arg }: { arg: { orderId: string; status: OrderStatus } }) =>
      updateOrderStatus(arg.orderId, arg.status),
    { throwOnError: false },
  );

  const {
    data: createMenu,
    error: createMenuError,
    mutate: mutateCreateMenu,
  } = useSWR(
    isCreateOpen && createLocationId
      ? staffLocationMenuKey(accountId, createLocationId)
      : null,
    ([, , , menuLocationId]) => getLocationMenu(menuLocationId),
  );
  const pickedItems = (quantities: ItemQuantities) =>
    (createMenu?.items ?? [])
      .filter((item) => quantities[item])
      .map((item) => ({ name: item, quantity: quantities[item] }));
  const selectedItems = pickedItems(itemQuantities);
  const selectedQuantity = selectedItems.reduce(
    (total, item) => total + item.quantity,
    0,
  );
  const generatedLabel = describeOrderItems(selectedItems);
  const isCreateMenuLoading =
    Boolean(createLocationId) &&
    createMenu === undefined &&
    createMenuError === undefined;
  // Without a menu (or when it cannot load) the flow starts at the label step.
  const isItemsStep = Boolean(createMenu) && createStep === "items";

  // Changing items regenerates the label and replaces manual edits.
  function changeItems(quantities: ItemQuantities) {
    setItemQuantities(quantities);
    setLabel(describeOrderItems(pickedItems(quantities)));
    setLabelError(undefined);
  }

  const locationNames = new Map(
    locations.map((location) => [location.id, location.name]),
  );
  const loadError = locationsError ?? ordersError;

  function openCreate() {
    if (!isCreateOpen)
      recordPanelAction(
        "order_creation_form_opened",
        isCreatingOrder
          ? createLocationId
          : (locationId ?? enabledLocations[0]?.id),
      );
    if (isCreatingOrder) {
      setIsCreateOpen(true);

      return;
    }
    resetCreateOrder();
    setItemQuantities({});
    setLabel("");
    setLabelError(undefined);
    setCreateStep("items");
    setCreateLocationId(locationId ?? enabledLocations[0]?.id);
    setIsCreateOpen(true);
  }

  function showDetails(order: StaffOrder) {
    setDetailsOrder(order);
    setIsDetailsOpen(true);
  }

  // Proceed and Create share a position, so the second tap of a double-tap must not create.
  function changeCreateStep(step: "items" | "label") {
    stepChangedAt.current = performance.now();
    setCreateStep(step);
  }

  function isCreateStepSettling() {
    return performance.now() - stepChangedAt.current < 400;
  }

  async function createOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isItemsStep) {
      changeCreateStep("label");

      return;
    }
    if (!createLocationId || isCreatingOrder || isCreateStepSettling()) return;

    const validation = createOrderInputSchema.safeParse({
      label,
      items: selectedItems,
    });

    if (!validation.success) {
      setLabelError(validation.error.issues[0]?.message);

      return;
    }

    setLabelError(undefined);
    const order = await triggerCreateOrder({
      locationId: createLocationId,
      input: validation.data,
    });

    if (!order) {
      // A rejected selection usually means the menu changed; show its current items.
      if (selectedItems.length > 0) void mutateCreateMenu();

      return;
    }

    setLabel("");
    setItemQuantities({});
    setCreateStep("items");
    setIsCreateOpen(false);
    toast.success(`Utworzono zamówienie #${order.number}`);
    showDetails(order);
    void mutateOrders().catch(() => undefined);
  }

  async function updateStatus(
    order: StaffOrder,
    status: OrderStatus,
  ): Promise<boolean> {
    if (isUpdatingOrder) return false;

    resetUpdateOrder();
    // Cancellation reports failures inside its confirmation dialog.
    const updated = await triggerUpdateOrder(
      { orderId: order.id, status },
      status === "CANCELED"
        ? undefined
        : {
            onError: (error) =>
              toastError(
                "Nie udało się zmienić statusu zamówienia",
                getErrorMessage(error),
              ),
          },
    );

    if (!updated) return false;
    if (status === "CANCELED")
      toast.success(`Anulowano zamówienie #${order.number}`);

    const isTerminal =
      updated.status === "COMPLETED" || updated.status === "CANCELED";

    if (isTerminal && detailsOrder?.id === updated.id) setIsDetailsOpen(false);
    void mutateOrders().catch(() => undefined);

    return true;
  }

  function selectLocation(nextLocationId?: string) {
    if (isCreatingOrder || isUpdatingOrder) return;
    onSelectLocation(nextLocationId);
    resetCreateOrder();
    resetUpdateOrder();
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="sr-only">Zamówienia</h1>
        {enabledLocations.length > 0 && (
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                aria-label="Nowe zamówienie"
                className="rounded-md"
                size="lg"
                onPress={openCreate}
              >
                <PlusIcon size={20} />
                Nowe zamówienie
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>Nowe zamówienie</Tooltip.Content>
          </Tooltip>
        )}
      </div>

      {loadError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wczytać zamówień</Alert.Title>
            <Alert.Description>{getErrorMessage(loadError)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {areLocationsLoading ? (
        <LoadingIndicator label="Wczytywanie lokali" />
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
            <LoadingIndicator label="Wczytywanie zamówień" />
          ) : (
            <div className="grid gap-10 md:grid-cols-2 md:gap-0 md:divide-x md:divide-separator">
              {(["IN_PREPARATION", "READY"] as const).map((status) => {
                const lane = laneDetails[status];
                const laneOrders = orders.filter(
                  (order) => order.status === status,
                );

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
                            order={order}
                            showLocation={locationId === undefined}
                            onCancel={(selectedOrder) => {
                              if (isUpdatingOrder) return;
                              resetUpdateOrder();
                              setCancelOrder(selectedOrder);
                            }}
                            onShowDetails={showDetails}
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
        className={createMenu ? "order-create-popup" : undefined}
        isOpen={isCreateOpen}
        size={createMenu ? "xl" : "md"}
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
                isDisabled={isCreatingOrder}
                label="Lokal"
                locations={enabledLocations}
                selectedId={createLocationId}
                showAll={false}
                onChange={(nextLocationId) => {
                  setCreateLocationId(nextLocationId);
                  setItemQuantities({});
                  setLabel("");
                  setLabelError(undefined);
                  setCreateStep("items");
                }}
              />
            )}
            {isCreateMenuLoading ? (
              <LoadingIndicator label="Wczytywanie menu" />
            ) : isItemsStep && createMenu ? (
              <div key="items" className="order-create-step">
                <OrderItemPicker
                  isDisabled={isCreatingOrder}
                  menu={createMenu}
                  quantities={itemQuantities}
                  onChange={changeItems}
                />
              </div>
            ) : (
              <div
                key="label"
                className="order-create-step flex flex-col gap-3"
              >
                {createMenuError && (
                  <p className="text-sm text-danger" role="alert">
                    Nie udało się wczytać menu lokalu. Możesz utworzyć
                    zamówienie z etykietą.
                  </p>
                )}
                <FormTextField
                  fullWidth
                  isDisabled={isCreatingOrder}
                  isInvalid={Boolean(labelError)}
                  label="Etykieta zamówienia (opcjonalnie)"
                  maxLength={maximumOrderLabelLength}
                  name="order-label"
                  value={label}
                  onChange={(value) => {
                    setLabel(value);
                    setLabelError(undefined);
                  }}
                />
                {labelError && (
                  <p className="text-sm text-danger">{labelError}</p>
                )}
                {selectedItems.length > 0 && label !== generatedLabel && (
                  <Button
                    className="self-start"
                    isDisabled={isCreatingOrder}
                    size="sm"
                    variant="tertiary"
                    onPress={() => {
                      setLabel(generatedLabel);
                      setLabelError(undefined);
                    }}
                  >
                    <ArrowCounterClockwiseIcon aria-hidden="true" size={16} />
                    Przywróć z pozycji
                  </Button>
                )}
              </div>
            )}
          </PanelPopup.Body>
          <PanelPopup.Footer>
            {createMenu && !isItemsStep ? (
              <Button
                isDisabled={isCreatingOrder}
                variant="tertiary"
                onPress={() => changeCreateStep("items")}
              >
                <ArrowLeftIcon aria-hidden="true" size={18} />
                Wstecz
              </Button>
            ) : (
              <Button
                aria-label="Anuluj"
                variant="tertiary"
                onPress={() => {
                  if (!isCreateStepSettling()) setIsCreateOpen(false);
                }}
              >
                Anuluj
              </Button>
            )}
            {/* One button keeps Proceed and Create in the same place. */}
            <Button
              isDisabled={isCreateMenuLoading}
              isPending={isCreatingOrder}
              type="submit"
            >
              {isItemsStep ? (
                <>
                  Dalej
                  {selectedQuantity > 0 && (
                    <span className="tabular-nums">({selectedQuantity})</span>
                  )}
                  <ArrowRightIcon aria-hidden="true" size={18} />
                </>
              ) : (
                <>
                  <PlusIcon aria-hidden="true" size={18} />
                  {isCreatingOrder ? "Tworzenie…" : "Utwórz"}
                </>
              )}
            </Button>
          </PanelPopup.Footer>
        </form>
      </PanelPopup>

      {canManageLocations && (
        <LocationCreationModal
          accountId={accountId}
          isOpen={isCreateLocationOpen}
          onCreated={(location) => onSelectLocation(location.id)}
          onOpenChange={setIsCreateLocationOpen}
        />
      )}

      <PanelPopup
        aria-label={
          detailsOrder ? `Zamówienie #${detailsOrder.number}` : undefined
        }
        isOpen={isDetailsOpen}
        size={detailsOrder?.items.length ? "xl" : "lg"}
        onOpenChange={(open) => {
          if (!open) setIsDetailsOpen(false);
        }}
      >
        <PanelPopup.Header className="flex-col items-center justify-center px-16 pb-2">
          <PanelPopup.Heading className="text-center text-3xl font-semibold tabular-nums">
            #{detailsOrder?.number}
          </PanelPopup.Heading>
          {detailsOrder?.label && (
            <p className="break-words text-center text-lg secondary-text">
              {detailsOrder.label}
            </p>
          )}
        </PanelPopup.Header>
        <PanelPopup.Body className="grid items-start gap-6 px-6 pb-8 pt-2 sm:px-10 md:auto-cols-fr md:grid-flow-col">
          {detailsOrder && (
            <>
              <OrderQrCode accountId={accountId} order={detailsOrder} />
              {detailsOrder.items.length > 0 && (
                // h-0 + min-h-full: the QR code alone sets the row height and the list scrolls within it.
                <section
                  aria-label="Pozycje zamówienia"
                  className="flex flex-col md:order-first md:h-0 md:min-h-[max(100%,18rem)] md:self-stretch"
                >
                  <h3 className="pb-2 text-sm secondary-text">Pozycje</h3>
                  <ul className="flex flex-col md:min-h-0 md:flex-1 md:overflow-y-auto">
                    {detailsOrder.items.map((item, index) => (
                      <li
                        key={`${index}-${item.name}`}
                        className="flex gap-3 border-t border-separator py-2 text-base text-foreground"
                      >
                        <span className="w-8 shrink-0 text-right font-semibold tabular-nums">
                          {item.quantity}×
                        </span>
                        <span className="min-w-0 break-words">{item.name}</span>
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </>
          )}
        </PanelPopup.Body>
      </PanelPopup>

      <PanelPopup
        isOpen={Boolean(cancelOrder)}
        role="alertdialog"
        size="sm"
        onOpenChange={(open) => {
          if (!open) setCancelOrder(undefined);
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="warning" />
          <PanelPopup.Heading>
            Anuluj zamówienie #{cancelOrder?.number}?
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body>
          <p>Tej operacji nie można cofnąć.</p>
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
            Wróć
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
            <CloseIcon aria-hidden="true" size={18} /> Anuluj zamówienie
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>
    </div>
  );
}

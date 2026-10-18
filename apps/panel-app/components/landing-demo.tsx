"use client";

import { Button, Spinner, ToggleButton, Tooltip } from "@heroui/react";
import {
  BellIcon,
  BellSlashIcon,
  MoonIcon,
  QrCodeIcon,
  SunIcon,
  XIcon,
} from "@phosphor-icons/react";
import Image from "next/image";
import { useReducer } from "react";
import useSWR from "swr";

import { panelAppUrl } from "@/src/config/public-environment";
import { orderQrCode } from "@/src/order-qr-code";

type DemoStatus = "IN_PREPARATION" | "READY" | "COMPLETED";

type DemoState = {
  order: { number: number; status: DemoStatus; inQueue: boolean } | null;
  nextNumber: number;
  phone: "home" | "order";
  notificationsEnabled: boolean;
  phoneDark: boolean;
  notification: { id: number; text: string } | null;
};

type DemoAction =
  | { type: "add" }
  | { type: "scan" }
  | { type: "advance"; status: "READY" | "COMPLETED"; id: number }
  | { type: "leaveQueue"; number: number }
  | { type: "dismissNotification"; id: number }
  | { type: "close" }
  | { type: "toggleNotifications" }
  | { type: "togglePhoneTheme" };

const DEMO_LABEL = "2× Pierogi ruskie, lemoniada";
const NOTIFICATION_DURATION_MS = 5000;
const QUEUE_EXIT_DELAY_MS = 1500;

const queueStatusCopy: Record<DemoStatus, string> = {
  IN_PREPARATION: "W przygotowaniu",
  READY: "Gotowe",
  COMPLETED: "Odebrane",
};

// Mirrors the customer application's order summary copy.
const customerStatusCopy: Record<DemoStatus, string> = {
  IN_PREPARATION: "Przygotowujemy Twoje zamówienie",
  READY: "Gotowe do odbioru",
  COMPLETED: "Zamówienie zrealizowane",
};

const notificationCopy = {
  READY: (number: number) => `Zamówienie #${number} jest gotowe do odbioru.`,
  COMPLETED: (number: number) => `Zamówienie #${number} zostało zrealizowane.`,
};

const initialState: DemoState = {
  order: null,
  nextNumber: 12,
  phone: "home",
  // As if the guest had enabled notifications earlier.
  notificationsEnabled: true,
  phoneDark: false,
  notification: null,
};

function reducer(state: DemoState, action: DemoAction): DemoState {
  switch (action.type) {
    case "add":
      // The demo holds one order at a time.
      if (state.order?.inQueue) return state;

      return {
        ...state,
        order: {
          number: state.nextNumber,
          status: "IN_PREPARATION",
          inQueue: true,
        },
        nextNumber: state.nextNumber + 1,
        phone: "home",
        notification: null,
      };
    case "scan":
      if (state.order?.status !== "IN_PREPARATION" || state.phone !== "home") {
        return state;
      }

      return { ...state, phone: "order" };
    case "advance": {
      if (!state.order) return state;
      const tracked = state.phone === "order";

      return {
        ...state,
        order: { ...state.order, status: action.status },
        notification:
          tracked && state.notificationsEnabled
            ? {
                id: action.id,
                text: notificationCopy[action.status](state.order.number),
              }
            : state.notification,
      };
    }
    case "leaveQueue":
      if (state.order?.number !== action.number) return state;

      // An untracked completed order disappears; a tracked one stays on the phone.
      return state.phone === "order"
        ? { ...state, order: { ...state.order, inQueue: false } }
        : { ...state, order: null };
    case "dismissNotification":
      return state.notification?.id === action.id
        ? { ...state, notification: null }
        : state;
    case "close":
      return { ...state, order: null, phone: "home", notification: null };
    case "toggleNotifications":
      return { ...state, notificationsEnabled: !state.notificationsEnabled };
    case "togglePhoneTheme":
      return { ...state, phoneDark: !state.phoneDark };
  }
}

export function LandingDemo() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const { order, phone } = state;
  const queuedOrder = order?.inQueue ? order : null;
  const tracked = phone === "order";

  const scan = () => dispatch({ type: "scan" });

  const advance = (status: "READY" | "COMPLETED") => {
    if (!order) return;
    const id = Date.now();

    dispatch({ type: "advance", status, id });
    setTimeout(
      () => dispatch({ type: "dismissNotification", id }),
      NOTIFICATION_DURATION_MS,
    );
    if (status === "COMPLETED") {
      const number = order.number;

      setTimeout(
        () => dispatch({ type: "leaveQueue", number }),
        QUEUE_EXIT_DELAY_MS,
      );
    }
  };

  return (
    <div className="landing-demo">
      <section
        aria-label="Panel restauracji (demo)"
        className="landing-demo-panel"
      >
        <header className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="landing-eyebrow">Panel restauracji</p>
            <p className="text-lg font-semibold">Bistro Pod Lipą</p>
          </div>
          <Button
            isDisabled={Boolean(queuedOrder)}
            onPress={() => dispatch({ type: "add" })}
          >
            Nowe zamówienie
          </Button>
        </header>
        {queuedOrder ? (
          <div aria-live="polite" className="landing-demo-order">
            <DemoQrCode />
            <div className="min-w-0">
              <p className="landing-demo-order-number">#{queuedOrder.number}</p>
              <p className="secondary-text text-sm">
                {queueStatusCopy[queuedOrder.status]}
                {tracked && " · śledzone"}
              </p>
            </div>
            <div className="landing-demo-order-actions">
              {queuedOrder.status === "IN_PREPARATION" && phone === "home" && (
                <Button variant="secondary" onPress={scan}>
                  Zeskanuj telefonem
                </Button>
              )}
              {queuedOrder.status === "IN_PREPARATION" && (
                <Button onPress={() => advance("READY")}>Gotowe</Button>
              )}
              {queuedOrder.status === "READY" && (
                <Button onPress={() => advance("COMPLETED")}>Odebrane</Button>
              )}
            </div>
          </div>
        ) : (
          <p className="secondary-text py-12 text-center">
            Brak zamówień. Kliknij „Nowe zamówienie”, żeby zacząć.
          </p>
        )}
      </section>

      <section aria-label="Telefon gościa (demo)" className="landing-phone">
        <div
          className={`landing-phone-screen ${state.phoneDark ? "dark" : "light"}`}
        >
          <span aria-hidden="true" className="landing-phone-island" />
          <div aria-live="polite" className="landing-phone-notifications">
            {state.notification && (
              <div
                key={state.notification.id}
                className="landing-phone-notification"
              >
                <Image
                  alt=""
                  className="size-7 flex-none"
                  height={28}
                  src="/icons/kairos-app-icon.svg"
                  width={28}
                />
                <div className="min-w-0 flex-1">
                  <p className="flex justify-between gap-2 text-xs">
                    <span className="font-semibold">Kairos</span>
                    <span className="secondary-text">teraz</span>
                  </p>
                  <p className="text-sm">{state.notification.text}</p>
                </div>
              </div>
            )}
          </div>
          <div className="landing-phone-app">
            <div className="flex justify-end gap-1">
              <Tooltip delay={500}>
                <Tooltip.Trigger className="flex">
                  <Button
                    isIconOnly
                    aria-label={
                      state.notificationsEnabled
                        ? "Wyłącz powiadomienia"
                        : "Włącz powiadomienia"
                    }
                    size="sm"
                    variant={
                      state.notificationsEnabled ? "primary" : "tertiary"
                    }
                    onPress={() => dispatch({ type: "toggleNotifications" })}
                  >
                    {/* Same icons as the customer app: the bell shows the next action. */}
                    {state.notificationsEnabled ? (
                      <BellSlashIcon size={18} />
                    ) : (
                      <BellIcon size={18} />
                    )}
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>
                  {state.notificationsEnabled
                    ? "Wyłącz powiadomienia"
                    : "Włącz powiadomienia"}
                </Tooltip.Content>
              </Tooltip>
              <Tooltip delay={500}>
                <Tooltip.Trigger className="flex">
                  <ToggleButton
                    isIconOnly
                    aria-label="Tryb ciemny"
                    className="landing-phone-theme"
                    isSelected={state.phoneDark}
                    size="sm"
                    onChange={() => dispatch({ type: "togglePhoneTheme" })}
                  >
                    {state.phoneDark ? (
                      <MoonIcon size={18} weight="fill" />
                    ) : (
                      <SunIcon size={18} />
                    )}
                  </ToggleButton>
                </Tooltip.Trigger>
                <Tooltip.Content>Tryb ciemny</Tooltip.Content>
              </Tooltip>
            </div>
            <PhoneView dispatch={dispatch} state={state} onScan={scan} />
          </div>
        </div>
      </section>
    </div>
  );
}

function PhoneView({
  state,
  dispatch,
  onScan,
}: {
  state: DemoState;
  dispatch: (action: DemoAction) => void;
  onScan: () => void;
}) {
  const { order, phone } = state;

  if (phone === "order" && order) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center text-center">
        <p className="landing-phone-order-number">
          <span className="sr-only">Zamówienie </span>#{order.number}
        </p>
        <p className="secondary-text mt-1 max-w-[20ch] text-xs">{DEMO_LABEL}</p>
        <p className="landing-phone-status mt-4">
          {customerStatusCopy[order.status]}
        </p>
        {order.status === "COMPLETED" && (
          <Button
            className="mt-8"
            variant="secondary"
            onPress={() => dispatch({ type: "close" })}
          >
            <XIcon aria-hidden="true" size={18} />
            Zamknij zamówienie
          </Button>
        )}
      </div>
    );
  }

  const canScan = order?.status === "IN_PREPARATION";

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3">
      <Button
        aria-label="Zeskanuj kod QR"
        className="landing-phone-scan"
        isDisabled={!canScan}
        variant="tertiary"
        onPress={onScan}
      >
        <span className="landing-phone-scan-label">Zeskanuj kod QR</span>
        <QrCodeIcon aria-hidden="true" size={104} />
      </Button>
      <p className="secondary-text min-h-10 px-3 text-center text-xs">
        {canScan
          ? "Kod QR jest na ekranie przy kasie."
          : "Najpierw dodaj zamówienie w panelu."}
      </p>
    </div>
  );
}

function DemoQrCode() {
  // The encoder loads only after the first demo order is added.
  const { data: qrCode } = useSWR(["landing-demo-qr", panelAppUrl], ([, url]) =>
    orderQrCode(url),
  );

  return (
    <div className="landing-demo-qr">
      {qrCode ? (
        <Image
          unoptimized
          alt="Kod QR zamówienia (demo)"
          className="size-full"
          height={56}
          src={qrCode}
          width={56}
        />
      ) : (
        <Spinner aria-label="Generowanie kodu QR" size="sm" />
      )}
    </div>
  );
}

"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import {
  NotificationApiError,
  disablePushSubscription,
  fromBase64Url,
  getNotificationConfiguration,
  reconcilePushSubscription,
  replacePushSubscription,
  serializePushSubscription,
} from "@/src/api/customer-notifications";
import {
  readGuideDismissal,
  rememberGuideDismissal,
  requiresNotificationInstallation,
  shouldShowNotificationGuide,
} from "@/src/pwa/notification-onboarding";
import { migrateLegacyRecentlyTrackedOrders } from "@/src/pwa/recently-tracked-orders";
import { updateApplicationBadge } from "@/src/pwa/badge";
import {
  pruneTerminalTrackedOrders,
  readNotificationMetadata,
  readNotificationTrackingReferences,
  type SerializedPushSubscription,
  updateNotificationMetadata,
} from "@/src/pwa/storage";

export type NotificationState =
  | "blocked"
  | "disabled"
  | "enabled"
  | "error"
  | "installation-required"
  | "loading"
  | "unsupported";

type CustomerNotificationContextValue = {
  disable: () => Promise<void>;
  dismissMessage: () => void;
  enable: () => Promise<void>;
  enrollOrder: (trackingReference: string) => Promise<void>;
  message: string | null;
  state: NotificationState;
  pendingAction: "requesting-permission" | "enabling" | "disabling" | null;
  guideOpen: boolean;
  dismissGuide: () => void;
  requestEnable: () => void;
  showGuideForOrder: () => void;
};

const CustomerNotificationContext =
  createContext<CustomerNotificationContextValue | null>(null);

export function CustomerPwaProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [state, setState] = useState<NotificationState>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [guideOpen, setGuideOpen] = useState(false);
  const [pendingAction, setPendingAction] =
    useState<CustomerNotificationContextValue["pendingAction"]>(null);
  const changingNotifications = useRef(false);
  const guideDismissed = useRef(false);
  const firstOrderSeen = useRef(false);
  const synchronizing = useRef<Promise<void> | null>(null);
  const dismissMessage = useCallback(() => setMessage(null), []);

  const synchronize = useCallback(async () => {
    if (requiresNotificationInstallation()) {
      setState("installation-required");
      setMessage(null);

      return;
    }
    if (!supportsWebPush()) {
      setState("unsupported");
      setMessage(null);

      return;
    }
    if (process.env.NODE_ENV !== "production") {
      setState("unsupported");
      setMessage(null);

      return;
    }
    if (Notification.permission === "denied") {
      try {
        await updateNotificationMetadata({
          notificationsEnabled: false,
          enrolledTrackingReferences: [],
        });
      } catch {
        // The blocked-permission guidance remains useful without IndexedDB.
      }
      setState("blocked");
      setMessage(
        "Powiadomienia są zablokowane. Włącz je w ustawieniach przeglądarki lub urządzenia.",
      );

      return;
    }
    const metadata = await readNotificationMetadata();

    if (metadata.notificationsEnabled === false) {
      setState("disabled");
      setMessage(null);

      return;
    }
    if (Notification.permission !== "granted") {
      setState("disabled");
      setMessage(null);

      return;
    }
    if (metadata.notificationsEnabled !== true) {
      setState("disabled");
      setMessage(null);

      return;
    }
    if (synchronizing.current) {
      return synchronizing.current;
    }
    const operation = synchronizeGrantedSubscription();

    synchronizing.current = operation;
    try {
      await operation;
      setState("enabled");
      setMessage(null);
      setGuideOpen(false);
    } catch (error) {
      setState("error");
      setMessage(notificationErrorMessage(error));
    } finally {
      synchronizing.current = null;
    }
  }, []);

  useEffect(() => {
    let active = true;

    void (async () => {
      if (
        process.env.NODE_ENV === "production" &&
        "serviceWorker" in navigator
      ) {
        try {
          await navigator.serviceWorker.register("/sw.js", {
            scope: "/",
            updateViaCache: "none",
          });
        } catch {
          // Core tracking remains available when service-worker registration fails.
        }
      }
      await migrateLegacyRecentlyTrackedOrders();
      await pruneTerminalTrackedOrders();
      await updateApplicationBadge();
      if (active) {
        await synchronize();
      }
    })();
    const handleOnline = () => {
      if (active && !changingNotifications.current) {
        void synchronize();
      }
    };

    window.addEventListener("online", handleOnline);

    return () => {
      active = false;
      window.removeEventListener("online", handleOnline);
    };
  }, [synchronize]);

  const enable = useCallback(async () => {
    if (changingNotifications.current) return;
    setMessage(null);
    if (requiresNotificationInstallation()) {
      setState("installation-required");

      return;
    }
    if (!supportsWebPush() || process.env.NODE_ENV !== "production") {
      setState("unsupported");
      setMessage(
        "Powiadomienia są niedostępne w tej przeglądarce lub konfiguracji. Nadal możesz śledzić zamówienie.",
      );

      return;
    }
    if (!navigator.onLine) {
      setState("disabled");
      setMessage(
        "Połącz się z internetem i spróbuj ponownie włączyć powiadomienia.",
      );

      return;
    }
    if (Notification.permission === "denied") {
      setState("blocked");
      setMessage(
        "Powiadomienia są zablokowane. Włącz je w ustawieniach przeglądarki lub urządzenia.",
      );

      return;
    }
    changingNotifications.current = true;
    setPendingAction("requesting-permission");
    try {
      const permission =
        Notification.permission === "granted"
          ? "granted"
          : await Notification.requestPermission();

      if (permission !== "granted") {
        setState(permission === "denied" ? "blocked" : "disabled");
        setMessage(
          permission === "denied"
            ? "Powiadomienia są zablokowane. Włącz je w ustawieniach przeglądarki lub urządzenia."
            : "Nie udzielono zgody na powiadomienia.",
        );

        return;
      }
      setPendingAction("enabling");
      await updateNotificationMetadata({ notificationsEnabled: true });
      await synchronize();
    } catch {
      setState("error");
      setMessage(
        "Nie udało się zapisać powiadomień w tej przeglądarce. Nadal możesz śledzić zamówienie.",
      );
    } finally {
      changingNotifications.current = false;
      setPendingAction(null);
    }
  }, [synchronize]);

  const disable = useCallback(async () => {
    if (changingNotifications.current) return;
    if (!supportsWebPush()) {
      return;
    }
    if (!navigator.onLine) {
      setMessage(
        "Połącz się z internetem i spróbuj ponownie wyłączyć powiadomienia.",
      );

      return;
    }
    changingNotifications.current = true;
    setPendingAction("disabling");
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();

      if (subscription) {
        await disablePushSubscription(serializePushSubscription(subscription));
      }
      await updateNotificationMetadata({
        notificationsEnabled: false,
        enrolledTrackingReferences: [],
        pendingSubscriptionReplacement: undefined,
        registeredEndpoint: undefined,
      });
      await subscription?.unsubscribe();
      await updateApplicationBadge(0);
      setState("disabled");
      setMessage(null);
    } catch (error) {
      setState("enabled");
      setMessage(notificationErrorMessage(error));
    } finally {
      changingNotifications.current = false;
      setPendingAction(null);
    }
  }, []);

  const enrollOrder = useCallback(
    async (trackingReference: string) => {
      if (
        state !== "enabled" ||
        !navigator.onLine ||
        changingNotifications.current
      ) {
        return;
      }
      const metadata = await readNotificationMetadata();

      if (
        (metadata.enrolledTrackingReferences ?? []).includes(trackingReference)
      ) {
        return;
      }
      await synchronize();
    },
    [state, synchronize],
  );

  const dismissGuide = useCallback(() => {
    guideDismissed.current = true;
    rememberGuideDismissal();
    setGuideOpen(false);
  }, []);

  const shouldShowGuide = useCallback(
    (automatic: boolean) =>
      shouldShowNotificationGuide({
        state,
        installationRequired: requiresNotificationInstallation(),
        permission:
          typeof Notification === "undefined"
            ? undefined
            : Notification.permission,
        dismissed: guideDismissed.current || readGuideDismissal(),
        automatic,
      }),
    [state],
  );

  const showGuideForOrder = useCallback(() => {
    if (state === "loading" || firstOrderSeen.current) return;
    firstOrderSeen.current = true;
    if (shouldShowGuide(true)) setGuideOpen(true);
  }, [state, shouldShowGuide]);

  const requestEnable = useCallback(() => {
    if (shouldShowGuide(false)) {
      setGuideOpen(true);
    } else {
      dismissGuide();
      void enable();
    }
  }, [dismissGuide, enable, shouldShowGuide]);

  const value = useMemo<CustomerNotificationContextValue>(
    () => ({
      disable,
      dismissMessage,
      enable,
      enrollOrder,
      message,
      state,
      pendingAction,
      guideOpen,
      dismissGuide,
      requestEnable,
      showGuideForOrder,
    }),
    [
      disable,
      dismissMessage,
      enable,
      enrollOrder,
      message,
      state,
      pendingAction,
      guideOpen,
      dismissGuide,
      requestEnable,
      showGuideForOrder,
    ],
  );

  return (
    <CustomerNotificationContext value={value}>
      {children}
    </CustomerNotificationContext>
  );
}

export function useCustomerNotifications(): CustomerNotificationContextValue {
  const value = useContext(CustomerNotificationContext);

  if (!value) {
    throw new Error(
      "useCustomerNotifications must be used within CustomerPwaProvider.",
    );
  }

  return value;
}

async function synchronizeGrantedSubscription(): Promise<void> {
  const configuration = await getNotificationConfiguration();
  const applicationServerKey = fromBase64Url(
    configuration.applicationServerKey,
  );

  await navigator.serviceWorker.register("/sw.js", {
    scope: "/",
    updateViaCache: "none",
  });
  const registration = await navigator.serviceWorker.ready;
  let subscription = await registration.pushManager.getSubscription();
  let replacedSubscription: SerializedPushSubscription | null = null;

  if (
    subscription &&
    !keysEqual(subscription.options.applicationServerKey, applicationServerKey)
  ) {
    replacedSubscription = serializePushSubscription(subscription);
    await subscription.unsubscribe();
    subscription = null;
  }
  subscription ??= await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey,
  });
  const trackingReferences = await readNotificationTrackingReferences();
  const serialized = serializePushSubscription(subscription);
  const metadata = await readNotificationMetadata();
  const previousSubscription =
    metadata.pendingSubscriptionReplacement?.previous ?? replacedSubscription;

  if (previousSubscription) {
    await updateNotificationMetadata({
      pendingSubscriptionReplacement: {
        previous: previousSubscription,
        current: serialized,
      },
    });
    await replacePushSubscription(
      previousSubscription,
      serialized,
      trackingReferences,
    );
  }
  await reconcilePushSubscription(serialized, trackingReferences);

  await updateNotificationMetadata({
    notificationsEnabled: true,
    enrolledTrackingReferences: trackingReferences,
    pendingSubscriptionReplacement: undefined,
    registeredEndpoint: subscription.endpoint,
  });
  await updateApplicationBadge();
}

function supportsWebPush(): boolean {
  return (
    typeof window !== "undefined" &&
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window
  );
}

function keysEqual(current: ArrayBuffer | null, expected: Uint8Array): boolean {
  if (!current) {
    return false;
  }
  const currentBytes = new Uint8Array(current);

  return (
    currentBytes.length === expected.length &&
    currentBytes.every((value, index) => value === expected[index])
  );
}

function notificationErrorMessage(error: unknown): string {
  if (
    error instanceof NotificationApiError &&
    error.code === "CUSTOMER_PUSH_ENROLLMENT_LIMIT"
  ) {
    return "Osiągnięto limit odbiorców powiadomień dla tego zamówienia. Nadal możesz je śledzić.";
  }

  return "Nie udało się zaktualizować powiadomień. Sprawdź połączenie i spróbuj ponownie.";
}

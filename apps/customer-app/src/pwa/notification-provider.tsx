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
  removePushEnrollments,
  replacePushSubscription,
  serializePushSubscription,
} from "@/src/api/customer-notifications";
import { migrateLegacyRecentlyTrackedOrders } from "@/src/pwa/recently-tracked-orders";
import { updateApplicationBadge } from "@/src/pwa/badge";
import {
  clearTrackedOrders,
  pruneTerminalTrackedOrders,
  readNotificationMetadata,
  readTrackedOrders,
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
  clearOrders: () => Promise<boolean>;
  disable: () => Promise<void>;
  enable: () => Promise<void>;
  enrollOrder: (trackingReference: string) => Promise<void>;
  message: string | null;
  state: NotificationState;
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
  const synchronizing = useRef<Promise<void> | null>(null);

  const synchronize = useCallback(async () => {
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
        "Notifications are blocked. Restore them in your browser or device settings.",
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
      setState(isIosOutsideStandalone() ? "installation-required" : "disabled");
      setMessage(
        isIosOutsideStandalone()
          ? "Add Kairos to your Home Screen, open it there, then enable notifications."
          : null,
      );

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
      if (active) {
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
    setMessage(null);
    if (!supportsWebPush()) {
      setState("unsupported");

      return;
    }
    if (isIosOutsideStandalone()) {
      setState("installation-required");
      setMessage(
        "On iPhone or iPad, use Share → Add to Home Screen, then open Kairos from the Home Screen.",
      );

      return;
    }
    if (!navigator.onLine) {
      setState("disabled");
      setMessage(
        "Connect to the internet and try enabling notifications again.",
      );

      return;
    }
    if (Notification.permission === "denied") {
      setState("blocked");
      setMessage(
        "Notifications are blocked. Restore them in your browser or device settings.",
      );

      return;
    }
    const permission =
      Notification.permission === "granted"
        ? "granted"
        : await Notification.requestPermission();

    if (permission !== "granted") {
      setState(permission === "denied" ? "blocked" : "disabled");
      setMessage(
        permission === "denied"
          ? "Notifications are blocked. Restore them in your browser or device settings."
          : "Notification permission was not granted.",
      );

      return;
    }
    try {
      await updateNotificationMetadata({ notificationsEnabled: true });
      await synchronize();
    } catch {
      setState("error");
      setMessage(
        "Notifications could not be saved in this browser. Tracking still works normally.",
      );
    }
  }, [synchronize]);

  const disable = useCallback(async () => {
    if (!supportsWebPush()) {
      return;
    }
    if (!navigator.onLine) {
      setMessage(
        "Connect to the internet and try disabling notifications again.",
      );

      return;
    }
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
    }
  }, []);

  const enrollOrder = useCallback(
    async (trackingReference: string) => {
      if (state !== "enabled" || !navigator.onLine) {
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

  const clearOrders = useCallback(async (): Promise<boolean> => {
    const orders = await readTrackedOrders();
    const trackingReferences = orders.map(
      ({ trackingReference }) => trackingReference,
    );

    if (
      trackingReferences.length > 0 &&
      (await readNotificationMetadata()).notificationsEnabled === true
    ) {
      if (!navigator.onLine) {
        setMessage(
          "Connect to the internet before clearing notification-enabled orders.",
        );

        return false;
      }
      try {
        const registration = await navigator.serviceWorker.ready;
        const subscription = await registration.pushManager.getSubscription();

        if (subscription) {
          await removePushEnrollments(
            serializePushSubscription(subscription),
            trackingReferences,
          );
        }
      } catch (error) {
        setMessage(notificationErrorMessage(error));

        return false;
      }
    }
    await clearTrackedOrders();
    await updateApplicationBadge(0);
    setMessage(null);

    return true;
  }, []);

  const value = useMemo<CustomerNotificationContextValue>(
    () => ({
      clearOrders,
      disable,
      enable,
      enrollOrder,
      message,
      state,
    }),
    [clearOrders, disable, enable, enrollOrder, message, state],
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
  const orders = await readTrackedOrders();
  const trackingReferences = orders.map(
    ({ trackingReference }) => trackingReference,
  );
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
      metadata.csrfToken,
    );
  }
  const csrfToken = await reconcilePushSubscription(
    serialized,
    trackingReferences,
  );

  await updateNotificationMetadata({
    notificationsEnabled: true,
    enrolledTrackingReferences: trackingReferences,
    pendingSubscriptionReplacement: undefined,
    registeredEndpoint: subscription.endpoint,
    csrfToken,
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

function isIosOutsideStandalone(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  const navigatorWithStandalone = navigator as Navigator & {
    standalone?: boolean;
  };
  const ios = /iPad|iPhone|iPod/.test(navigator.userAgent);
  const standalone =
    window.matchMedia("(display-mode: standalone)").matches ||
    navigatorWithStandalone.standalone === true;

  return ios && !standalone;
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
    return "This order already has the maximum number of notification subscribers. Tracking still works normally.";
  }

  return "Notifications could not be updated. Check your connection and try again.";
}

import type { ReactElement } from "react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { CustomerPwaProvider } from "@/src/pwa/notification-provider";
import type { NotificationMetadata } from "@/src/pwa/storage";

const harness = vi.hoisted(() => ({
  effects: [] as (() => void | (() => void))[],
  metadata: { notificationsEnabled: true } as NotificationMetadata,
  readMetadata: vi.fn(),
  configuration: vi.fn(),
  enroll: vi.fn(),
  disable: vi.fn(),
}));

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  useCallback: (callback: unknown) => callback,
  useEffect: (effect: () => void | (() => void)) =>
    harness.effects.push(effect),
  useMemo: (factory: () => unknown) => factory(),
  useRef: (current: unknown) => ({ current }),
  useState: (initial: unknown) => [initial, vi.fn()],
}));
vi.mock("@/src/api/customer-notifications", () => ({
  NotificationApiError: class extends Error {},
  getNotificationConfiguration: harness.configuration,
  fromBase64Url: () => new Uint8Array([1]),
  serializePushSubscription: (subscription: unknown) => subscription,
  reconcilePushSubscription: harness.enroll,
  replacePushSubscription: vi.fn(),
  disablePushSubscription: harness.disable,
}));
vi.mock("@/src/pwa/storage", () => ({
  readNotificationMetadata: harness.readMetadata,
  updateNotificationMetadata: async (patch: NotificationMetadata) => {
    harness.metadata = { ...harness.metadata, ...patch };
  },
  readNotificationTrackingReferences: async () => ["order"],
  pruneTerminalTrackedOrders: vi.fn(),
}));
vi.mock("@/src/pwa/badge", () => ({ updateApplicationBadge: vi.fn() }));
vi.mock("@/src/pwa/notification-onboarding", () => ({
  requiresNotificationInstallation: () => false,
  shouldShowNotificationGuide: () => false,
  readGuideDismissal: () => false,
  rememberGuideDismissal: vi.fn(),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });

  return { promise, resolve };
}

let subscription: object | null;
let remoteEnabled: boolean;
let online: () => void;
const unsubscribe = vi.fn(async () => {
  subscription = null;
  return true;
});
const subscribe = vi.fn(async () => {
  subscription = {
    endpoint: "push",
    options: { applicationServerKey: new Uint8Array([1]).buffer },
    unsubscribe,
  };

  return subscription;
});

beforeEach(() => {
  vi.clearAllMocks();
  harness.effects = [];
  harness.metadata = { notificationsEnabled: true };
  harness.readMetadata.mockImplementation(async () => ({
    ...harness.metadata,
  }));
  harness.configuration.mockResolvedValue({ applicationServerKey: "key" });
  harness.enroll.mockImplementation(async () => {
    remoteEnabled = true;
  });
  harness.disable.mockImplementation(async () => {
    remoteEnabled = false;
  });
  subscription = null;
  remoteEnabled = false;
  vi.stubEnv("NODE_ENV", "production");
  vi.stubGlobal("Notification", { permission: "granted" });
  vi.stubGlobal("window", {
    PushManager: {},
    Notification: {},
    addEventListener: (_type: string, listener: () => void) => {
      online = listener;
    },
    removeEventListener: vi.fn(),
  });
  vi.stubGlobal("navigator", {
    onLine: true,
    serviceWorker: {
      register: vi.fn(),
      ready: Promise.resolve({
        pushManager: { getSubscription: async () => subscription, subscribe },
      }),
    },
  });
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

it.each(["consent read", "configuration request"])(
  "finishes opt-out after reconciliation blocked on %s",
  async (boundary) => {
    const entered = deferred<void>();
    const release = deferred<void>();
    const delayed =
      boundary === "consent read"
        ? harness.readMetadata
        : harness.configuration;

    delayed.mockImplementationOnce(async () => {
      const metadata = { ...harness.metadata };

      entered.resolve();
      await release.promise;

      return boundary === "consent read"
        ? metadata
        : { applicationServerKey: "key" };
    });
    const element = CustomerPwaProvider({ children: null }) as ReactElement<{
      value: { disable: () => Promise<void> };
    }>;
    const cleanup = harness.effects[0]();

    await entered.promise;
    const disabling = element.props.value.disable();

    online();
    await Promise.resolve();
    expect(harness.disable).not.toHaveBeenCalled();
    release.resolve();
    await disabling;
    online();
    await vi.waitFor(() =>
      expect(harness.readMetadata).toHaveBeenCalledTimes(3),
    );

    expect(remoteEnabled).toBe(false);
    expect(subscription).toBeNull();
    expect(harness.metadata.notificationsEnabled).toBe(false);
    expect(harness.metadata.enrolledTrackingReferences).toEqual([]);
    expect(harness.enroll).toHaveBeenCalledTimes(1);
    expect(harness.disable).toHaveBeenCalledTimes(1);
    if (cleanup) cleanup();
  },
);

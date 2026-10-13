import { afterAll, afterEach, beforeEach, expect, it, vi } from "vitest";

import { OrderTracker } from "@/app/orders/[trackingReference]/order-tracker";
import type { CustomerOrder } from "@/src/api/orders";

const reference = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
const harness = vi.hoisted(() => ({
  order: undefined as CustomerOrder | undefined,
  effects: [] as (() => void | (() => void))[],
  fetcher: undefined as ((key: readonly string[]) => Promise<CustomerOrder>) | undefined,
  getOrder: vi.fn(),
  source: undefined as { onopen: () => void; listener: (event: { data: string }) => void } | undefined,
  message: undefined as ((event: { data: unknown }) => void) | undefined,
}));

vi.hoisted(() => vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.example.test"));
vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  useState: (initial: unknown) => [typeof initial === "function" ? initial() : initial, vi.fn()],
  useEffect: (effect: () => void) => harness.effects.push(effect),
  useRef: (current: unknown) => ({ current }),
}));
vi.mock("swr", async (original) => ({
  ...(await original<typeof import("swr")>()),
  useSWRConfig: () => ({ cache: new Map() }),
  default: (_key: unknown, fetcher: typeof harness.fetcher) => {
    harness.fetcher = fetcher;
    return { data: harness.order, mutate: (candidate: CustomerOrder | ((current: CustomerOrder | undefined) => CustomerOrder)) => {
      harness.order = typeof candidate === "function" ? candidate(harness.order) : candidate;
      return Promise.resolve(harness.order);
    } };
  },
}));
vi.mock("swr/subscription", () => ({
  default: (key: unknown, subscribe: (key: unknown) => void) => key && subscribe(key),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/src/pwa/notification-provider", () => ({
  useCustomerNotifications: () => ({ enrollOrder: vi.fn(), showGuideForOrder: vi.fn() }),
}));
vi.mock("@/src/api/orders", async (original) => ({
  ...(await original<typeof import("@/src/api/orders")>()),
  getTrackedOrder: harness.getOrder,
}));
vi.mock("@/src/pwa/storage", async (original) => ({
  ...(await original<typeof import("@/src/pwa/storage")>()),
  readTrackedOrder: async () => null,
}));

afterAll(() => vi.unstubAllEnvs());
afterEach(() => vi.unstubAllGlobals());

const snapshot = (status: CustomerOrder["status"]) => ({
  eventId: "060f9c9d-1762-4fd2-a5a8-c11ddc00ff21", trackingReference: reference,
  number: 12, label: "A12", status, updatedAt: "2026-10-12T10:00:00Z", reviewInvitation: null,
});
beforeEach(() => {
  vi.clearAllMocks();
  harness.effects = [];
  harness.order = snapshot("IN_PREPARATION");
  vi.stubGlobal("EventSource", class {
    onopen = () => {};
    listener = (_event: { data: string }) => {};
    constructor() { harness.source = this; }
    addEventListener(_name: string, listener: typeof this.listener) { this.listener = listener; }
    removeEventListener() {}
    close() {}
  });
  vi.stubGlobal("navigator", { serviceWorker: {
    addEventListener: (_name: string, listener: typeof harness.message) => { harness.message = listener; },
    removeEventListener: vi.fn(),
  } });
});

it("applies stream snapshots and changes without REST and rejects reordered push/REST", async () => {
  OrderTracker({ trackingReference: reference });
  harness.effects.at(-1)?.();
  harness.source?.onopen();
  harness.source?.listener({ data: JSON.stringify(snapshot("READY")) });
  harness.source?.listener({ data: JSON.stringify(snapshot("IN_PREPARATION")) });
  expect(harness.order?.status).toBe("READY");
  expect(harness.getOrder).not.toHaveBeenCalled();

  harness.message?.({ data: { type: "KAIROS_ORDER_TRANSITION", trackingReference: reference,
    status: "COMPLETED", transitionedAt: "2026-10-12T09:00:00Z" } });
  harness.getOrder.mockResolvedValue(snapshot("IN_PREPARATION"));
  expect((await harness.fetcher?.(["tracked-order", reference]))?.status).toBe("COMPLETED");
  harness.source?.listener({ data: JSON.stringify(snapshot("READY")) });
  expect(harness.order?.status).toBe("COMPLETED");
});

import { beforeEach, expect, it, vi } from "vitest";

import { subscribeToOrderQueue } from "@/src/api/orders";

class FakeEventSource {
  static last: FakeEventSource;
  listeners: Record<string, (event: { data: string }) => void> = {};
  onerror?: () => void;
  close = vi.fn();
  constructor(public url: URL, public options: EventSourceInit) {
    FakeEventSource.last = this;
  }
  addEventListener(name: string, listener: (event: { data: string }) => void) {
    this.listeners[name] = listener;
  }
  emit(data: string) {
    this.listeners["order-queue"]({ data });
  }
}

beforeEach(() => {
  vi.stubGlobal("EventSource", FakeEventSource);
});

const order = {
  id: "00000000-0000-4000-8000-000000000001",
  locationId: "00000000-0000-4000-8000-000000000002",
  trackingReference: "00000000-0000-4000-8000-000000000003",
  number: 1,
  label: null,
  items: [{ name: "Burger", quantity: 2 }],
  status: "READY",
  createdAt: "2026-10-12T10:00:00Z",
  updatedAt: "2026-10-12T10:01:00Z",
};

it("credentialed queue stream accepts validated full snapshots including removals and closes on cleanup", () => {
  const snapshots = vi.fn();
  const close = subscribeToOrderQueue(order.locationId, snapshots, vi.fn());
  const source = FakeEventSource.last;

  expect(source.url.href).toBe(`https://api.example.test/api/orders/v1/events?locationId=${order.locationId}`);
  expect(source.options).toEqual({ withCredentials: true });
  source.emit(JSON.stringify([order]));
  source.emit("[]");
  expect(snapshots.mock.calls).toEqual([[[order]], [[]]]);
  close();
  expect(source.close).toHaveBeenCalled();
});

it("tenant stream omits location and invalid snapshots or disconnects request REST recovery", () => {
  const snapshots = vi.fn();
  const disconnected = vi.fn();

  subscribeToOrderQueue(undefined, snapshots, disconnected);
  const source = FakeEventSource.last;

  expect(source.url.search).toBe("");
  source.emit("not json");
  source.emit(JSON.stringify([{ ...order, status: "UNKNOWN" }]));
  source.onerror?.();
  expect(disconnected).toHaveBeenCalledTimes(3);
  expect(snapshots).not.toHaveBeenCalled();
  source.emit(JSON.stringify([order]));
  expect(snapshots).toHaveBeenCalledWith([order]);
});

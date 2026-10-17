import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { subscribeToOrderQueue } from "@/src/api/orders";

class FakeEventSource {
  static CLOSED = 2;
  static last: FakeEventSource;
  static count = 0;
  readyState = 1;
  listeners: Record<string, (event: { data: string }) => void> = {};
  onerror?: () => void;
  close = vi.fn();
  constructor(public url: URL, public options: EventSourceInit) {
    FakeEventSource.last = this;
    FakeEventSource.count++;
  }
  addEventListener(name: string, listener: (event: { data: string }) => void) {
    this.listeners[name] = listener;
  }
  emit(data: string) {
    this.listeners["order-queue"]({ data });
  }
}

beforeEach(() => {
  FakeEventSource.count = 0;
  vi.stubGlobal("EventSource", FakeEventSource);
});

afterEach(() => vi.useRealTimers());

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

it("recreates a permanently closed stream with backoff and stops after cleanup", () => {
  vi.useFakeTimers();
  // Midpoint jitter keeps the delays exact.
  vi.spyOn(Math, "random").mockReturnValue(0.5);
  const close = subscribeToOrderQueue(undefined, vi.fn(), vi.fn());

  FakeEventSource.last.readyState = FakeEventSource.CLOSED;
  FakeEventSource.last.onerror?.();
  vi.advanceTimersByTime(1_999);
  expect(FakeEventSource.count).toBe(1);
  vi.advanceTimersByTime(1);
  expect(FakeEventSource.count).toBe(2);

  FakeEventSource.last.readyState = FakeEventSource.CLOSED;
  FakeEventSource.last.onerror?.();
  vi.advanceTimersByTime(3_999);
  expect(FakeEventSource.count).toBe(2);
  vi.advanceTimersByTime(1);
  expect(FakeEventSource.count).toBe(3);

  FakeEventSource.last.readyState = FakeEventSource.CLOSED;
  FakeEventSource.last.onerror?.();
  close();
  vi.advanceTimersByTime(60_000);
  expect(FakeEventSource.count).toBe(3);
});

// @vitest-environment jsdom
import "fake-indexeddb/auto";

import { IDBFactory } from "fake-indexeddb";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { SWRConfig } from "swr";
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { OrderTracker } from "@/app/orders/[trackingReference]/order-tracker";
import { CustomerPwaProvider } from "@/src/pwa/notification-provider";

vi.hoisted(() => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.example");
});

afterAll(() => vi.unstubAllEnvs());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";

class FakeEventSource {
  static CLOSED = 2;
  static instances: FakeEventSource[] = [];
  readyState = 0;
  onopen?: () => void;
  onerror?: () => void;
  close = vi.fn();
  listeners = new Map<string, (event: MessageEvent<string>) => void>();
  addEventListener = vi.fn((type: string, listener: (event: MessageEvent<string>) => void) => this.listeners.set(type, listener));
  removeEventListener = vi.fn();
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
}

const fetchOrder = vi.fn();

function respondWith(status: string) {
  fetchOrder.mockImplementation(async () =>
    Response.json({ number: 12, label: "A12", status, updatedAt: "2026-10-09T20:00:00Z" }),
  );
}

async function renderOrder(status: string) {
  respondWith(status);
  render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <CustomerPwaProvider>
        <OrderTracker trackingReference={TRACKING_REFERENCE} />
      </CustomerPwaProvider>
    </SWRConfig>,
  );
  await screen.findByRole("heading", { level: 1 });
}

beforeEach(() => {
  FakeEventSource.instances = [];
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" });
  vi.stubGlobal("indexedDB", new IDBFactory());
  fetchOrder.mockReset();
  vi.stubGlobal("EventSource", FakeEventSource);
  vi.stubGlobal("fetch", fetchOrder);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("order exit controls", () => {
  it.each(["IN_PREPARATION", "READY"])("provides no close action while the order is %s", async (status) => {
    await renderOrder(status);
    expect(screen.queryByText("Zamknij zamówienie")).toBeNull();
  });

  it.each(["COMPLETED", "CANCELED"])("offers closing after the order is %s", async (status) => {
    await renderOrder(status);
    expect(screen.getByText("Zamknij zamówienie")).toBeTruthy();
  });
});

describe("order event stream", () => {
  it.each(["READY", "COMPLETED", "CANCELED"])("applies opening and reopening snapshots without REST when the order becomes %s", async (status) => {
    await renderOrder("IN_PREPARATION");
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const source = FakeEventSource.instances[0];
    const fetchesBeforeOpen = fetchOrder.mock.calls.length;
    act(() => source.onopen?.());
    const snapshot = (nextStatus: string) => source.listeners.get("order-status-changed")?.(new MessageEvent("order-status-changed", { data: JSON.stringify({ eventId: crypto.randomUUID(), trackingReference: TRACKING_REFERENCE, number: 12, label: "A12", status: nextStatus, updatedAt: "2026-10-09T20:01:00Z", reviewInvitation: null }) }));
    await act(async () => snapshot(status));
    await waitFor(() => expect(screen.queryByText("Zamknij zamówienie") !== null).toBe(status !== "READY"));
    expect(fetchOrder.mock.calls.length).toBe(fetchesBeforeOpen);
    if (status === "READY") {
      expect(screen.getByText("Gotowe do odbioru")).toBeTruthy();
      act(() => { source.onerror?.(); source.onopen?.(); });
      await act(async () => snapshot("COMPLETED"));
      await screen.findByText("Zamknij zamówienie");
      await act(async () => snapshot("IN_PREPARATION"));
      expect(screen.getByText("Zamknij zamówienie")).toBeTruthy();
      expect(fetchOrder.mock.calls.length).toBe(fetchesBeforeOpen);
    }
  });

  it("polls while disconnected and keeps a newer streamed state when REST is stale", async () => {
    await renderOrder("IN_PREPARATION");
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const source = FakeEventSource.instances[0];
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] });
    act(() => source.onopen?.());
    await act(async () => source.listeners.get("order-status-changed")?.(new MessageEvent("order-status-changed", { data: JSON.stringify({ eventId: crypto.randomUUID(), trackingReference: TRACKING_REFERENCE, number: 12, label: "A12", status: "READY", updatedAt: "2026-10-09T20:01:00Z" }) })));
    const connectedFetches = fetchOrder.mock.calls.length;
    await act(async () => vi.advanceTimersByTimeAsync(30_000));
    expect(fetchOrder.mock.calls.length).toBe(connectedFetches);
    let releasePoll!: (response: Response) => void;
    const staleResponse = new Promise<Response>((resolve) => { releasePoll = resolve; });
    fetchOrder.mockImplementation(() => staleResponse);
    act(() => source.onerror?.());
    await act(async () => vi.advanceTimersByTimeAsync(15_000));
    // The polling clock does not drain IndexedDB's separate task queue.
    vi.useRealTimers();
    await waitFor(() => expect(fetchOrder.mock.calls.length).toBe(connectedFetches + 1));
    await act(async () => releasePoll(Response.json({ number: 12, label: "A12", status: "IN_PREPARATION", updatedAt: "2026-10-09T20:00:00Z" })));
    expect(screen.getByText("Gotowe do odbioru")).toBeTruthy();
  });

  it("recreates a permanently closed stream with doubling backoff", async () => {
    await renderOrder("IN_PREPARATION");
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    vi.useFakeTimers();
    // Midpoint jitter keeps the delays exact.
    vi.spyOn(Math, "random").mockReturnValue(0.5);

    // A reconnecting (not closed) source recovers by itself and is left alone.
    FakeEventSource.instances[0].readyState = 0;
    act(() => FakeEventSource.instances[0].onerror?.());
    act(() => void vi.advanceTimersByTime(60_000));
    expect(FakeEventSource.instances).toHaveLength(1);

    FakeEventSource.instances[0].readyState = FakeEventSource.CLOSED;
    act(() => FakeEventSource.instances[0].onerror?.());
    act(() => void vi.advanceTimersByTime(1_999));
    expect(FakeEventSource.instances).toHaveLength(1);
    act(() => void vi.advanceTimersByTime(1));
    expect(FakeEventSource.instances).toHaveLength(2);

    FakeEventSource.instances[1].readyState = FakeEventSource.CLOSED;
    act(() => FakeEventSource.instances[1].onerror?.());
    act(() => void vi.advanceTimersByTime(3_999));
    expect(FakeEventSource.instances).toHaveLength(2);
    act(() => void vi.advanceTimersByTime(1));
    expect(FakeEventSource.instances).toHaveLength(3);

    // Unmounting with a reconnect pending cancels it.
    FakeEventSource.instances[2].readyState = FakeEventSource.CLOSED;
    act(() => FakeEventSource.instances[2].onerror?.());
    cleanup();
    act(() => void vi.advanceTimersByTime(60_000));
    expect(FakeEventSource.instances).toHaveLength(3);
  });

  it("stops reconnecting after ten refusals in a row and leaves the order to polling", async () => {
    await renderOrder("IN_PREPARATION");
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    vi.useFakeTimers();

    for (let refusal = 0; refusal < 11; refusal++) {
      const source = FakeEventSource.instances.at(-1)!;
      source.readyState = FakeEventSource.CLOSED;
      act(() => source.onerror?.());
      act(() => void vi.advanceTimersByTime(30_000));
    }

    expect(FakeEventSource.instances).toHaveLength(11);
  });
});

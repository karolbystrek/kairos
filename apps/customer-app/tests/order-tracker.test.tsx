// @vitest-environment jsdom
import "fake-indexeddb/auto";

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
  addEventListener = vi.fn();
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
    <SWRConfig value={{ provider: () => new Map() }}>
      <CustomerPwaProvider>
        <OrderTracker trackingReference={TRACKING_REFERENCE} />
      </CustomerPwaProvider>
    </SWRConfig>,
  );
  await screen.findByRole("heading", { level: 1 });
}

beforeEach(() => {
  FakeEventSource.instances = [];
  fetchOrder.mockReset();
  vi.stubGlobal("EventSource", FakeEventSource);
  vi.stubGlobal("fetch", fetchOrder);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
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
  it("refetches when the stream opens so a transition made before it opened is not lost", async () => {
    await renderOrder("IN_PREPARATION");
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const fetchesBeforeOpen = fetchOrder.mock.calls.length;

    respondWith("READY");
    act(() => FakeEventSource.instances[0].onopen?.());

    await waitFor(() => expect(fetchOrder.mock.calls.length).toBeGreaterThan(fetchesBeforeOpen));
  });

  it("recreates a permanently closed stream with doubling backoff", async () => {
    await renderOrder("IN_PREPARATION");
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    vi.useFakeTimers();

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

// @vitest-environment jsdom
import "fake-indexeddb/auto";

import { IDBFactory, IDBObjectStore } from "fake-indexeddb";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { CustomerPwaProvider, useCustomerNotifications } from "@/src/pwa/notification-provider";
import { readNotificationMetadata, rememberTrackedOrder, updateNotificationMetadata } from "@/src/pwa/storage";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => { resolve = complete; });
  return { promise, resolve };
}

class InstallingWorker extends EventTarget {
  state = "installing";
  transition(state: string) {
    this.state = state;
    this.dispatchEvent(new Event("statechange"));
  }
}

function Controls() {
  const { state, pendingAction, message, enable, disable } = useCustomerNotifications();
  return <><output data-testid="state">{state}</output><output data-testid="pending">{pendingAction}</output><p role="alert">{message}</p><button onClick={() => void enable()}>Enable</button><button onClick={() => void disable()}>Disable</button></>;
}
function Provider() { return <CustomerPwaProvider><Controls /></CustomerPwaProvider>; }

let subscription: ReturnType<typeof makeSubscription> | null;
let remoteEnabled: boolean;
const fetchMock = vi.fn();
const subscribe = vi.fn();
const register = vi.fn();
const getRegistration = vi.fn();
function makeSubscription(key = 1) {
  return {
    endpoint: `https://push.example/${key}`,
    expirationTime: null,
    options: { applicationServerKey: new Uint8Array([key]).buffer },
    getKey: () => new Uint8Array([1]).buffer,
    unsubscribe: vi.fn(async () => { subscription = null; return true; }),
  };
}
let registration: { active: object | null; installing: object | null; pushManager: { getSubscription: ReturnType<typeof vi.fn>; subscribe: typeof subscribe } };

beforeEach(async () => {
  vi.clearAllMocks();
  vi.stubEnv("NODE_ENV", "production");
  vi.stubGlobal("indexedDB", new IDBFactory());
  vi.stubGlobal("Notification", { permission: "granted" });
  vi.stubGlobal("PushManager", class {});
  subscription = null;
  remoteEnabled = false;
  subscribe.mockImplementation(async () => { subscription = makeSubscription(); return subscription; });
  registration = { active: {}, installing: null, pushManager: { getSubscription: vi.fn(async () => subscription), subscribe } };
  register.mockResolvedValue(registration);
  getRegistration.mockResolvedValue(registration);
  Object.defineProperty(navigator, "serviceWorker", { configurable: true, value: { register, getRegistration, ready: Promise.resolve(registration) } });
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/configuration")) return Response.json({ applicationServerKey: "AQ" });
    if (url.includes("csrf")) return Response.json({ token: "token" });
    if (options?.method === "PUT" || options?.method === "POST") remoteEnabled = true;
    if (options?.method === "DELETE") remoteEnabled = false;
    return new Response(null, { status: 204 });
  });
  vi.stubGlobal("fetch", fetchMock);
  await updateNotificationMetadata({ notificationsEnabled: true });
  await rememberTrackedOrder({ trackingReference: "order", number: 1, label: null, status: "IN_PREPARATION", updatedAt: "2026-10-09T20:00:00Z" });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); Reflect.deleteProperty(navigator, "serviceWorker"); });

it.each(["absent registration", "inactive registration", "registration rejection"])("recovers notification controls after %s", async (failure) => {
  if (failure === "inactive registration") registration.active = null;
  getRegistration.mockResolvedValue(failure === "inactive registration" ? registration : undefined);
  Object.defineProperty(navigator.serviceWorker, "ready", { value: new Promise(() => {}) });
  if (failure === "registration rejection") register.mockRejectedValue(new Error("registration failed"));
  const view = render(<Provider />);
  await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("error"));
  expect(screen.getByRole("alert").textContent).toBeTruthy();
  fireEvent.click(screen.getByText("Disable"));
  await waitFor(() => expect(screen.getByTestId("pending").textContent).toBe(""));
  expect((await readNotificationMetadata()).notificationsEnabled).toBe(true);
  registration.active = {};
  getRegistration.mockResolvedValue(registration);
  view.rerender(<Provider />);
  fireEvent.click(screen.getByText("Enable"));
  await waitFor(() => {
    expect(screen.getByTestId("state").textContent).toBe("enabled");
    expect(screen.getByTestId("pending").textContent).toBe("");
  });
});

it("waits for a legitimately installing worker to activate", async () => {
  const ready = deferred<typeof registration>();
  registration.active = null;
  const worker = new InstallingWorker();
  const removed = vi.spyOn(worker, "removeEventListener");
  registration.installing = worker;
  Object.defineProperty(navigator.serviceWorker, "ready", { value: ready.promise });
  render(<Provider />);
  await waitFor(() => expect(fetchMock).toHaveBeenCalled());
  expect(screen.getByTestId("state").textContent).toBe("loading");
  await act(async () => { registration.active = {}; registration.installing = null; ready.resolve(registration); });
  await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("enabled"));
  expect(removed).toHaveBeenCalledWith("statechange", expect.any(Function));
});

it.each([false, true])("recovers when worker installation fails (queued opt-out: %s)", async (optOut) => {
  const worker = new InstallingWorker();
  const removed = vi.spyOn(worker, "removeEventListener");
  registration.active = null;
  registration.installing = worker;
  Object.defineProperty(navigator.serviceWorker, "ready", { value: new Promise(() => {}) });
  render(<Provider />);
  await waitFor(() => expect(getRegistration).toHaveBeenCalled());
  if (optOut) fireEvent.click(screen.getByText("Disable"));
  await act(async () => {
    registration.installing = null;
    worker.transition("redundant");
  });
  await waitFor(() => {
    expect(screen.getByTestId("state").textContent).toBe(optOut ? "enabled" : "error");
    expect(screen.getByTestId("pending").textContent).toBe("");
  });
  expect(screen.getByRole("alert").textContent).toBeTruthy();
  expect((await readNotificationMetadata()).notificationsEnabled).toBe(true);
  expect(removed).toHaveBeenCalledWith("statechange", expect.any(Function));
  registration.active = {};
  fireEvent.click(screen.getByText("Enable"));
  await waitFor(() => {
    expect(screen.getByTestId("state").textContent).toBe("enabled");
    expect(screen.getByTestId("pending").textContent).toBe("");
  });
  expect(screen.getByRole("alert").textContent).toBe("");
});

it.each(["consent read", "configuration request", "subscription lookup"])("finishes opt-out after reconciliation blocked on %s", async (boundary) => {
  const entered = deferred<void>();
  const release = deferred<void>();
  if (boundary === "consent read") {
    const get = IDBObjectStore.prototype.get;
    let metadataReads = 0;
    vi.spyOn(IDBObjectStore.prototype, "get").mockImplementation(function (this: IDBObjectStore, key) {
      const request = get.call(this, key);
      // Badging reads consent first; pause the provider's following consent read.
      if (key === "notification" && ++metadataReads === 2) {
        Object.defineProperty(request, "onsuccess", {
          set(listener: (event: Event) => void) {
            request.addEventListener("success", async (event) => {
              entered.resolve();
              await release.promise;
              listener.call(request, event);
            });
          },
        });
      }
      return request;
    });
  } else if (boundary === "configuration request") {
    const normalFetch = fetchMock.getMockImplementation()!;
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url.endsWith("/configuration")) { entered.resolve(); await release.promise; }
      return normalFetch(url, options);
    });
  } else {
    registration.pushManager.getSubscription.mockImplementationOnce(async () => { entered.resolve(); await release.promise; return subscription; });
  }
  render(<Provider />);
  await entered.promise;
  fireEvent.click(screen.getByText("Disable"));
  act(() => window.dispatchEvent(new Event("online")));
  await act(async () => release.resolve());
  await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("disabled"));
  act(() => window.dispatchEvent(new Event("online")));
  await waitFor(async () => expect((await readNotificationMetadata()).enrolledTrackingReferences).toEqual([]));
  expect(remoteEnabled).toBe(false);
  expect(subscription).toBeNull();
  expect((await readNotificationMetadata()).notificationsEnabled).toBe(false);
  expect(screen.getByTestId("pending").textContent).toBe("");
});

it("replaces an old push key before reconciling and opting out", async () => {
  subscription = makeSubscription(2);
  const previous = subscription;
  render(<Provider />);
  await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("enabled"));
  expect(previous.unsubscribe).toHaveBeenCalledOnce();
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith("/subscription-replacement"))).toBe(true);
  fireEvent.click(screen.getByText("Disable"));
  await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("disabled"));
  expect(remoteEnabled).toBe(false);
});

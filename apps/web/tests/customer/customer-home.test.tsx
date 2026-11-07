// @vitest-environment jsdom
import "fake-indexeddb/auto";

import { IDBFactory } from "fake-indexeddb";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { CustomerHome } from "@/app/(customer)/o/customer-home";
import { CustomerPwaProvider } from "@/src/pwa/notification-provider";

const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

vi.mock("next/navigation", () => ({ useRouter: () => router }));

beforeEach(() => {
  vi.stubGlobal("indexedDB", new IDBFactory());
  vi.stubGlobal("matchMedia", () => ({ matches: false }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("opens the scanner with a full page load so it receives the camera policy", async () => {
  const assign = vi.fn();

  vi.stubGlobal("location", { ...window.location, assign });
  render(
    <CustomerPwaProvider>
      <CustomerHome installationTrackingReference={null} />
    </CustomerPwaProvider>,
  );
  fireEvent.click(await screen.findByRole("button", { name: "Zeskanuj kod QR" }));

  expect(assign).toHaveBeenCalledWith("/o/scan");
  expect(router.push).not.toHaveBeenCalled();
});

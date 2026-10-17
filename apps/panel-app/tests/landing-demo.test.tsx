import { fireEvent, screen, within } from "@testing-library/react";
import { expect, it } from "vitest";

import { renderWithSWR, stubFetch } from "./helpers";

import { LandingDemo } from "@/components/landing-demo";

it("runs one order from the queue to the guest's phone without calling the API", async () => {
  const calls = stubFetch(() => new Response(null, { status: 500 }));

  renderWithSWR(<LandingDemo />);
  const panel = within(screen.getByRole("region", { name: "Panel restauracji (demo)" }));
  const phone = within(screen.getByRole("region", { name: "Telefon gościa (demo)" }));
  const add = panel.getByRole("button", { name: "Nowe zamówienie" });

  fireEvent.click(add);
  // The demo holds a single order.
  expect(add.hasAttribute("disabled")).toBe(true);

  fireEvent.click(phone.getByRole("button", { name: "Zeskanuj kod QR" }));
  await phone.findByText("Przygotowujemy Twoje zamówienie", {}, { timeout: 2000 });

  fireEvent.click(panel.getByRole("button", { name: "Gotowe" }));
  phone.getByText("Gotowe do odbioru");
  phone.getByText("Zamówienie #12 jest gotowe do odbioru.");

  fireEvent.click(panel.getByRole("button", { name: "Odebrane" }));
  // A completed order leaves the queue, freeing it for the next demo order.
  await panel.findByText(/Brak zamówień/, {}, { timeout: 2500 });
  expect(add.hasAttribute("disabled")).toBe(false);

  fireEvent.click(phone.getByRole("button", { name: "Zamknij zamówienie" }));
  phone.getByRole("button", { name: "Zeskanuj kod QR" });

  expect(calls.filter(({ url }) => url.origin === "https://api.example.test")).toEqual([]);
});

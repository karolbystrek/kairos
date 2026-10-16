import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";

import { apiCalls, deferred, fakeApi, location, renderWithSWR } from "./helpers";

import { OrderManagement } from "@/components/order-management";

const ids = { one: "00000000-0000-4000-8000-000000000001", other: "00000000-0000-4000-8000-000000000002" };
let routes: Parameters<typeof fakeApi>[0];
let calls: ReturnType<typeof fakeApi>;

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  routes = {
    "GET /api/locations/v1": () => [location(ids.one, "Cafe")],
    "GET /api/orders/v1": () => [],
    [`GET /api/locations/v1/${ids.one}/menu`]: () => ({ menu: null }),
    "POST /api/usage-analytics/v1/actions": () => undefined,
  };
  calls = fakeApi(new Proxy({}, { get: (_, key: string) => routes[key] }));
});

function queue() {
  const props = { accountId: "staff", canManageLocations: true, canViewTenantOrders: true, onSelectLocation: () => {} };
  const view = renderWithSWR(<OrderManagement {...props} />);
  const user = userEvent.setup();

  return {
    user,
    rerender: () => view.rerender(<OrderManagement {...props} />),
    // HeroUI's tooltip trigger repeats the button's role and name, so pick the real button.
    openForm: async () => user.click((await screen.findAllByRole("button", { name: "Nowe zamówienie" })).find((element) => element.tagName === "BUTTON")!),
    async closeForm() {
      await user.keyboard("{Escape}");
      await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    },
  };
}
const actions = () => calls
  .filter(({ url }) => url.pathname === "/api/usage-analytics/v1/actions")
  .map(({ init }) => JSON.parse(String(init.body)).actions[0]);

it("an opened queue counts once after location loading, not on rerenders", async () => {
  const locations = deferred();

  routes["GET /api/locations/v1"] = () => locations.promise;
  const view = queue();

  expect(actions()).toEqual([]);
  locations.resolve([location(ids.one, "Cafe")]);
  await screen.findByRole("heading", { name: "W przygotowaniu" });
  view.rerender();
  view.rerender();
  await waitFor(() => expect(actions()).toEqual([{ event: "queue_opened" }]));
});

it("each real form opening counts its default location even from the tenant queue", async () => {
  const view = queue();

  await view.openForm();
  await view.closeForm();
  await view.openForm();
  await waitFor(() => expect(actions()).toEqual([
    { event: "queue_opened" },
    { event: "order_creation_form_opened", locationId: ids.one },
    { event: "order_creation_form_opened", locationId: ids.one },
  ]));
});

it("initial queue loading does not show headings or an empty queue", async () => {
  const locations = deferred();

  routes["GET /api/locations/v1"] = () => locations.promise;
  queue();
  expect((await screen.findByRole("status")).textContent).toBe("Wczytywanie lokali");
  expect(screen.queryByRole("heading", { name: "W przygotowaniu" })).toBeNull();
  expect(screen.queryByText("Brak zamówień w przygotowaniu")).toBeNull();
  locations.resolve([location(ids.one, "Cafe")]);
  expect(await screen.findByRole("heading", { name: "W przygotowaniu" })).toBeTruthy();
  expect(await screen.findByText("Brak zamówień w przygotowaniu")).toBeTruthy();
});

it("dismiss and reopen cannot duplicate order creation by switching the queue filter", async () => {
  const creation = deferred();

  routes["GET /api/locations/v1"] = () => [location(ids.one, "Cafe"), location(ids.other, "Other")];
  routes["POST /api/orders/v1"] = () => creation.promise;
  const view = queue();

  // A location without a menu opens directly at the label step.
  await view.openForm();
  const dialog = await screen.findByRole("dialog");

  await view.user.click(await within(dialog).findByRole("button", { name: "Utwórz" }));
  await waitFor(() => expect(apiCalls(calls)).toContain("POST /api/orders/v1"));
  await view.closeForm();
  const filter = screen.getAllByRole("button", { name: /Lokal kolejki/ }).find((element) => element.tagName === "BUTTON")!;

  expect(filter.hasAttribute("disabled")).toBe(true);
  await view.openForm();
  await view.user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: /Tworzenie/ }));
  expect(apiCalls(calls).filter((call) => call === "POST /api/orders/v1")).toHaveLength(1);
});

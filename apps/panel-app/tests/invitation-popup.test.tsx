import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";

import { apiCalls, body, confirmHold, deferred, fakeApi, location, renderWithSWR, staffAccount, status } from "./helpers";

import { AccountManagement } from "@/components/account-management";
import type { TenantAccount } from "@/src/api/authentication";

const ids = {
  location: "00000000-0000-4000-8000-000000000001",
  tenant: "00000000-0000-4000-8000-0000000000b1",
  member: "00000000-0000-4000-8000-000000000050",
};
const invitation = (overrides: Record<string, unknown> = {}) => ({
  id: "00000000-0000-4000-8000-000000000060", email: "invitee@example.com", locationId: ids.location,
  locationName: "Location", role: "OPERATOR", issuedByEmail: "owner@example.com",
  createdAt: "2026-01-01T00:00:00Z", expiresAt: "2099-01-01T00:00:00Z", state: "PENDING", deliveryState: "QUEUED",
  ...overrides,
});
const member = {
  id: ids.member, tenantId: ids.tenant, locationId: ids.location, email: "member@example.com", role: "OPERATOR",
  status: "ENABLED", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
};
let routes: Parameters<typeof fakeApi>[0];
let calls: ReturnType<typeof fakeApi>;

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  routes = {
    "GET /api/locations/v1": () => [location(ids.location)],
    "GET /api/accounts/v1": () => [],
    "GET /api/account-invitations/v1": () => [],
    "POST /api/account-invitations/v1": () => invitation(),
  };
  calls = fakeApi(new Proxy({}, { get: (_, key: string) => routes[key] }));
});

function setup() {
  renderWithSWR(<AccountManagement account={staffAccount() as TenantAccount} />);
  const user = userEvent.setup();

  return {
    user,
    // HeroUI's tooltip trigger repeats the button's role and name, so pick the real button.
    open: async () => user.click((await screen.findAllByRole("button", { name: "Nowe konto" })).find((element) => element.tagName === "BUTTON")!),
    dialog: () => screen.getByRole("dialog"),
    email: () => screen.getByRole("textbox", { name: "E-mail zapraszanej osoby" }) as HTMLInputElement,
    async dismiss() {
      await user.keyboard("{Escape}");
      await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    },
    cards: () => screen.queryAllByRole("button", { name: /^Pokaż / }),
  };
}
const invitationPosts = () => apiCalls(calls).filter((call) => call.startsWith("POST /api/account-invitations"));

it("dismiss and reopen preserves pending creation and shows queued email without exposing a link", async () => {
  const creation = deferred();

  routes["POST /api/account-invitations/v1"] = () => creation.promise;
  const view = setup();

  await view.open();
  await view.user.type(view.email(), "invitee@example.com");
  await view.user.click(screen.getByRole("button", { name: "Utwórz zaproszenie" }));
  await view.dismiss();
  await view.open();
  await view.user.click(within(view.dialog()).getByRole("button", { name: /Tworzenie/ }));
  expect(invitationPosts()).toHaveLength(1);
  expect(body(calls, "/api/account-invitations/v1")).toMatchObject({ email: "invitee@example.com", replaceExisting: false });
  await view.dismiss();
  creation.resolve(invitation());
  expect(await screen.findByRole("heading", { name: "Zaproszenie wysłane do kolejki" })).toBeTruthy();
  expect(view.dialog().textContent).not.toMatch(/https?:|#invitation|Sekret/);
  await view.dismiss();
  await view.open();
  await view.user.click(within(view.dialog()).getByRole("button", { name: "Gotowe" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
});

it("account and invitation cards open only their details, and dismissal returns to the collection", async () => {
  routes["GET /api/accounts/v1"] = () => [member];
  routes["GET /api/account-invitations/v1"] = () => [invitation({ role: "MANAGER" })];
  const view = setup();

  await waitFor(() => expect(view.cards()).toHaveLength(2));
  await view.user.click(screen.getByRole("button", { name: /^Pokaż zaproszenie dla invitee@example.com/ }));
  expect(screen.getAllByRole("dialog").map((dialog) => dialog.getAttribute("aria-label"))).toEqual(["Zaproszenie dla invitee@example.com"]);
  await view.dismiss();
  await view.user.click(screen.getByRole("button", { name: /^Pokaż konto member@example.com/ }));
  expect(screen.getAllByRole("dialog").map((dialog) => dialog.getAttribute("aria-label"))).toEqual(["Konto member@example.com"]);
  await view.dismiss();
});

it("invitation loading preserves loaded accounts", async () => {
  routes["GET /api/accounts/v1"] = () => [member];
  routes["GET /api/account-invitations/v1"] = () => new Promise(() => {});
  const view = setup();

  await waitFor(() => expect(view.cards()).toHaveLength(1));
  expect(screen.getByRole("status").textContent).toBe("Wczytywanie zaproszeń");
});

it("invitation failure preserves loaded accounts and offers invitation retry", async () => {
  routes["GET /api/accounts/v1"] = () => [member];
  routes["GET /api/account-invitations/v1"] = status(503);
  const view = setup();
  const retry = await screen.findByRole("button", { name: "Spróbuj ponownie wczytać zaproszenia" });

  expect(view.cards()).toHaveLength(1);
  const before = apiCalls(calls).filter((call) => call === "GET /api/account-invitations/v1").length;

  await view.user.click(retry);
  await waitFor(() => expect(apiCalls(calls).filter((call) => call === "GET /api/account-invitations/v1").length).toBe(before + 1));
});

it("invitation creation rejects invalid email with a field error before requesting email delivery", async () => {
  const view = setup();

  await view.open();
  await view.user.type(view.email(), "invalid");
  // The browser's own email check stops the button; submit directly to reach the component's validation.
  fireEvent.submit(view.email().closest("form")!);
  expect(await within(view.dialog()).findByText("Podaj poprawny adres e-mail.")).toBeTruthy();
  expect(invitationPosts()).toEqual([]);
  await view.user.clear(view.email());
  await view.user.type(view.email(), "invitee@example.com");
  expect(within(view.dialog()).queryByText("Podaj poprawny adres e-mail.")).toBeNull();
});

it("duplicate invitation requires explicit replacement confirmation", async () => {
  routes["POST /api/account-invitations/v1"] = (_, init) =>
    JSON.parse(String(init.body)).replaceExisting ? invitation() : status(409)();
  const view = setup();

  await view.open();
  await view.user.type(view.email(), "invitee@example.com");
  await view.user.click(screen.getByRole("button", { name: "Utwórz zaproszenie" }));
  const confirm = await screen.findByRole("button", { name: /Przytrzymaj, aby zastąpić zaproszenie/ });

  expect(invitationPosts()).toHaveLength(1);
  expect(view.email().disabled).toBe(true);
  confirmHold(confirm);
  await waitFor(() => expect(invitationPosts()).toHaveLength(2));
  expect(body(calls, "/api/account-invitations/v1")).toMatchObject({ replaceExisting: true });
});

it("pending failed delivery can be resent only through confirmation", async () => {
  routes["GET /api/account-invitations/v1"] = () => [invitation({ deliveryState: "FAILED" })];
  routes["POST /api/account-invitations/v1/00000000-0000-4000-8000-000000000060/resend"] = () => invitation({ id: "00000000-0000-4000-8000-000000000061" });
  const view = setup();

  await view.user.click(await screen.findByRole("button", { name: /^Pokaż zaproszenie/ }));
  expect(invitationPosts()).toEqual([]);
  confirmHold(screen.getByRole("button", { name: /Przytrzymaj, aby wysłać\s+ponownie/ }));
  await waitFor(() => expect(invitationPosts()).toEqual(["POST /api/account-invitations/v1/00000000-0000-4000-8000-000000000060/resend"]));
});

it("redeemed invitations are represented by their account, not listed", async () => {
  routes["GET /api/account-invitations/v1"] = () => [invitation({ state: "REDEEMED" })];
  const view = setup();

  expect(await screen.findByText("Brak kont pracowników")).toBeTruthy();
  expect(view.cards()).toEqual([]);
});

it.each(["REVOKED", "REPLACED", "EXPIRED"])("%s invitation remains in history without mutation controls", async (state) => {
  routes["GET /api/account-invitations/v1"] = () => [invitation({ state })];
  const view = setup();

  await view.user.click(await screen.findByRole("button", { name: /^Pokaż zaproszenie/ }));
  expect(within(view.dialog()).queryAllByRole("button", { name: /Przytrzymaj/ })).toEqual([]);
});

it("full invitation history renders without sorting or paging controls", async () => {
  routes["GET /api/account-invitations/v1"] = () => Array.from({ length: 25 }, (_, index) => invitation({
    id: `00000000-0000-4000-8000-0000000001${String(index).padStart(2, "0")}`, email: `invitee-${index}@example.com`,
  }));
  const view = setup();

  await waitFor(() => expect(view.cards()).toHaveLength(25));
  expect(screen.queryByLabelText("Kolejność zaproszeń")).toBeNull();
  expect(screen.queryByText(/strona zaproszeń/)).toBeNull();
});

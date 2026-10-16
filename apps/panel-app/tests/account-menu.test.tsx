import { act, cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { unstable_serialize } from "swr";
import { beforeEach, expect, it, vi } from "vitest";

import { apiCalls, fakeApi, location, renderWithSWR, staffAccount, status } from "./helpers";

import { StaffPanel } from "@/components/staff-panel";
import { currentAccountKey } from "@/src/api/cache-keys";

const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

vi.mock("next/navigation", () => ({ useRouter: () => router }));

const pendingVerification = { verificationRequired: true, email: "staff@example.com", resendAvailableAt: "2099-01-01T00:00:00Z" };
let routes: Parameters<typeof fakeApi>[0];
let calls: ReturnType<typeof fakeApi>;

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  router.push.mockReset();
  router.replace.mockReset();
  window.history.replaceState(null, "", "/dashboard");
  routes = {
    "GET /api/auth/v1/me": () => staffAccount(),
    "GET /api/auth/v1/verification": status(401),
    "GET /api/locations/v1": () => [location("00000000-0000-4000-8000-000000000001")],
    "GET /api/orders/v1": () => [],
    "POST /api/auth/v1/login": () => staffAccount(),
    "POST /api/auth/v1/logout": () => undefined,
    "POST /api/auth/v1/password": () => undefined,
    "POST /api/usage-analytics/v1/actions": () => undefined,
  };
  calls = fakeApi(new Proxy({}, { get: (_, key: string) => routes[key] }));
});

function panel(screenName: "dashboard" | "login" | "password" = "dashboard") {
  return renderWithSWR(<StaffPanel screen={screenName} />);
}
const never = () => new Promise<never>(() => {});
const tabs = () => screen.queryAllByRole("tab", { hidden: true }).map((tab) => tab.dataset.key);
const selected = () => screen.queryAllByRole("tab", { hidden: true }).find((tab) => tab.getAttribute("aria-selected") === "true")?.dataset.key;
const staffData = (cache: Map<string, { data?: unknown }>) =>
  Array.from(cache).filter(([key, state]) => key.includes("\"staff\"") && state.data !== undefined);
const panelActions = () => calls
  .filter(({ url }) => url.pathname === "/api/usage-analytics/v1/actions")
  .map(({ init }) => JSON.parse(String(init.body)).actions[0].event)
  .filter((event) => event !== "queue_opened");

async function signIn() {
  const user = userEvent.setup();

  await user.type(await screen.findByRole("textbox", { name: "E-mail" }), "staff@example.com");
  await user.type(screen.getByLabelText("Hasło"), "password");
  await user.click(screen.getByRole("button", { name: "Zaloguj się" }));
}

async function chooseAccountAction(name: string) {
  const user = userEvent.setup();

  await user.click(await screen.findByRole("button", { name: "Konto" }));
  await user.click(await screen.findByRole("menuitem", { name }));
}

it("signed-in login visitors go to the dashboard", async () => {
  panel("login");
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/dashboard"));
});

it("signed-out dashboard visitors go to login only after the session check", async () => {
  let finish = () => {};

  routes["GET /api/auth/v1/me"] = () => new Promise((resolve) => { finish = () => resolve(status(401)()); });
  panel();
  expect(await screen.findByLabelText("Sprawdzanie sesji")).toBeTruthy();
  expect(router.replace).not.toHaveBeenCalled();
  finish();
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
});

it("dashboard session failures retain recovery instead of redirecting", async () => {
  routes["GET /api/auth/v1/me"] = status(503);
  panel();
  expect(await screen.findByText("Logowanie jest niedostępne")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Spróbuj ponownie" })).toBeTruthy();
  expect(router.replace).not.toHaveBeenCalled();
});

it("an expired cached account on login does not redirect back to the dashboard", async () => {
  const { rerender, swr } = panel();

  await screen.findByRole("button", { name: "Konto" });
  routes["GET /api/auth/v1/me"] = status(401);
  await act(() => swr.mutate(currentAccountKey));
  router.replace.mockReset();
  rerender(<StaffPanel screen="login" />);
  expect(await screen.findByRole("heading", { name: "Witaj ponownie" })).toBeTruthy();
  expect(router.replace).not.toHaveBeenCalledWith("/dashboard");
});

it("successful login navigates to the dashboard after updating the session", async () => {
  routes["GET /api/auth/v1/me"] = status(401);
  panel("login");
  await signIn();
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/dashboard"));
  expect(apiCalls(calls)).toContain("POST /api/auth/v1/login");
});

it("sign-out happens without confirmation and clears the session", async () => {
  const { cache } = panel();

  await screen.findByRole("button", { name: "Konto" });
  expect(staffData(cache)).not.toEqual([]);
  await chooseAccountAction("Wyloguj się");
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
  expect(apiCalls(calls).filter((call) => call.includes("logout"))).toEqual(["POST /api/auth/v1/logout"]);
  expect(staffData(cache)).toEqual([]);
});

it("failed sign-out keeps the session, shows the error and prevents resubmission while pending", async () => {
  let fail = () => {};

  routes["POST /api/auth/v1/logout"] = () => new Promise((resolve) => { fail = () => resolve(status(503)()); });
  const { cache } = panel();

  await chooseAccountAction("Wyloguj się");
  const trigger = screen.getByRole("button", { name: "Konto" });

  await waitFor(() => expect(trigger.hasAttribute("disabled")).toBe(true));
  await userEvent.setup().click(trigger);
  expect(screen.queryByRole("menuitem", { name: "Wyloguj się" })).toBeNull();
  await act(async () => fail());
  expect(await screen.findByText("Nie udało się wylogować")).toBeTruthy();
  expect(apiCalls(calls).filter((call) => call.includes("logout"))).toHaveLength(1);
  expect(staffData(cache)).not.toEqual([]);
  expect(router.replace).not.toHaveBeenCalledWith("/login");
});

it("password change opens its page and ends on login with a confirmation", async () => {
  panel();
  await chooseAccountAction("Zmień hasło");
  expect(router.push).toHaveBeenCalledWith("/dashboard/password");
  cleanup();

  const user = userEvent.setup();

  panel("password");
  await user.type(await screen.findByLabelText("Obecne hasło"), "current-password");
  await user.type(screen.getByLabelText("Nowe hasło"), "replacement-password");
  await user.type(screen.getByLabelText("Potwierdź hasło"), "replacement-password");
  await user.click(screen.getByRole("button", { name: "Zmień hasło" }));
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login?password-changed"));
  cleanup();

  router.replace.mockReset();
  routes["GET /api/auth/v1/me"] = status(401);
  panel("password");
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
});

it("workspace entries count once without counting rerenders or the selected tab", async () => {
  routes["GET /api/auth/v1/me"] = () => staffAccount(["MANAGE_LOCATIONS", "MANAGE_MENUS", "MANAGE_EXTERNAL_INTEGRATIONS"]);
  const user = userEvent.setup();
  const { rerender } = panel();
  const tab = async (name: string) => user.click(await screen.findByRole("tab", { name }));

  await tab("Menu");
  rerender(<StaffPanel screen="dashboard" />);
  await tab("Menu");
  await tab("Integracje");
  await tab("Zamówienia");
  await tab("Menu");
  await waitFor(() => expect(panelActions()).toEqual(["menu_editor_opened", "integration_settings_opened", "menu_editor_opened"]));
});

it("analytics tabs follow capabilities and revoked platform access leaves its tab", async () => {
  routes["GET /api/auth/v1/me"] = () => staffAccount(["MANAGE_ORDERS"]);
  const { swr } = panel();

  await screen.findByRole("button", { name: "Konto" });
  expect(tabs()).toEqual([]);
  await act(() => swr.mutate(currentAccountKey, staffAccount(["MANAGE_ORDERS", "VIEW_ANALYTICS", "VIEW_PLATFORM_ANALYTICS"]), { revalidate: false }));
  expect(tabs()).toEqual(["orders", "analytics", "platform"]);
  await userEvent.setup().click(screen.getByRole("tab", { name: "Platforma" }));
  await waitFor(() => expect(selected()).toBe("platform"));
  await act(() => swr.mutate(currentAccountKey, staffAccount(["MANAGE_ORDERS", "VIEW_ANALYTICS"]), { revalidate: false }));
  expect(tabs()).toEqual(["orders", "analytics"]);
  expect(selected()).toBe("orders");
});

it("unverified login stays outside the staff session and displays verification", async () => {
  routes["GET /api/auth/v1/me"] = status(401);
  routes["POST /api/auth/v1/login"] = () => pendingVerification;
  const { cache } = panel("login");

  await signIn();
  expect(await screen.findByRole("heading", { name: "Potwierdź adres e-mail" })).toBeTruthy();
  expect(router.replace).not.toHaveBeenCalled();
  expect(cache.get(unstable_serialize(currentAccountKey))?.data).toBeUndefined();
  expect(staffData(cache)).toEqual([]);
});

it("login restores a pending verification after reload", async () => {
  routes["GET /api/auth/v1/me"] = status(401);
  routes["GET /api/auth/v1/verification"] = () => pendingVerification;
  panel("login");
  expect(await screen.findByRole("heading", { name: "Potwierdź adres e-mail" })).toBeTruthy();
  expect(router.replace).not.toHaveBeenCalled();
});

it("direct links and reload preserve the workspace while locations load", async () => {
  routes["GET /api/auth/v1/me"] = () => staffAccount(["MANAGE_LOCATIONS", "MANAGE_EXTERNAL_INTEGRATIONS"]);
  routes["GET /api/locations/v1"] = never;
  window.location.hash = "integrations";
  panel();
  await waitFor(() => expect(selected()).toBe("integrations"));
  await userEvent.setup().click(screen.getByRole("tab", { name: "Lokale" }));
  expect(window.location.hash).toBe("#locations");
  cleanup();

  panel();
  await waitFor(() => expect(selected()).toBe("locations"));
  window.location.hash = "integrations";
  await waitFor(() => expect(selected()).toBe("integrations"));
  window.location.hash = "locations";
  await waitFor(() => expect(selected()).toBe("locations"));
});

it("unknown, inaccessible and onboarding-disabled fragments fall back to orders", async () => {
  window.location.hash = "unknown";
  routes["GET /api/auth/v1/me"] = () => staffAccount(["MANAGE_LOCATIONS"]);
  const { swr } = panel();

  await waitFor(() => expect(selected()).toBe("orders"));
  for (const hash of ["menus", "accounts", "integrations", "analytics", "platform"]) {
    window.location.hash = hash;
    await new Promise((resolve) => window.addEventListener("hashchange", resolve, { once: true }));
    expect(selected()).toBe("orders");
  }
  routes["GET /api/locations/v1"] = () => [];
  await act(async () => {
    await swr.mutate(currentAccountKey, staffAccount(["MANAGE_LOCATIONS", "MANAGE_EXTERNAL_INTEGRATIONS"]), { revalidate: false });
    await swr.mutate((key) => Array.isArray(key) && key.includes("locations"));
  });
  window.location.hash = "integrations";
  await screen.findByRole("dialog");
  expect(tabs()).toContain("integrations");
  expect(selected()).toBe("orders");
});

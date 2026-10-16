import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";

import { apiCalls, body, csrf, renderWithSWR, stubFetch, stubNavigation } from "./helpers";

import { RegistrationForm } from "@/components/registration-form";

const account = {
  accountId: "12345678-1234-4234-8234-123456789abc", email: "owner@example.com",
  tenantId: "12345678-1234-4234-8234-123456789abd", tenantRole: "ADMIN", assignment: null, capabilities: [],
};
const preview = { email: "invited@example.com", locationName: "Restaurant", role: "OPERATOR", expiresAt: "2099-01-01T00:00:00Z" };
let assign: ReturnType<typeof stubNavigation>;

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  window.history.replaceState(null, "", "/account-registration#invitation=invitation-token");
  assign = stubNavigation();
});

async function submitRegistration({ invited = false, fails = false, termsAccepted = true, verificationRequired = false } = {}) {
  const calls = stubFetch((url) => {
    if (csrf(url)) return Response.json({ token: "csrf" });
    if (url.pathname === "/api/auth/v1/me" || url.pathname === "/api/auth/v1/verification")
      return new Response(null, { status: 401 });
    if (url.pathname === "/api/account-invitation-previews/v1") return Response.json(preview);
    if (fails) throw new TypeError("Offline");

    return Response.json(verificationRequired
      ? { verificationRequired: true, email: "owner@example.com", resendAvailableAt: "2099-01-01T00:00:00Z" }
      : account);
  });
  const user = userEvent.setup();

  renderWithSWR(<RegistrationForm invited={invited} />);
  if (invited) await screen.findByText("Restaurant");
  else await user.type(screen.getByRole("textbox", { name: "E-mail" }), "owner@example.com");
  await user.type(screen.getByLabelText("Hasło"), "long-password");
  await user.type(screen.getByLabelText("Potwierdź hasło"), "long-password");
  if (termsAccepted) await user.click(screen.getByRole("checkbox", { name: "Akceptuję Regulamin Kairos" }));
  const submit = screen.getByRole("button", { name: "Utwórz konto" });

  const { value, readOnly } = screen.getByRole("textbox", { name: "E-mail" }) as HTMLInputElement;

  await vi.waitFor(() => expect(submit.hasAttribute("disabled")).toBe(false));
  await user.click(submit);

  return { calls, email: { value, readOnly }, registrations: () => apiCalls(calls).filter((call) => call.startsWith("POST") && !call.includes("previews")) };
}

it.each([
  [false, "POST /api/tenant-registrations/v1"],
  [true, "POST /api/account-invitation-redemptions/v1"],
])("registration (invited: %s) opens the dashboard after success", async (invited, request) => {
  const view = await submitRegistration({ invited });

  await vi.waitFor(() => expect(assign).toHaveBeenCalledWith("/dashboard"));
  expect(view.registrations()).toEqual([request]);
});

it("failed registration does not navigate away", async () => {
  await submitRegistration({ fails: true });
  expect(await screen.findByRole("status")).toBeTruthy();
  expect(assign).not.toHaveBeenCalled();
});

it("invited registration fixes the email from the invitation instead of editable form state", async () => {
  const view = await submitRegistration({ invited: true });
  expect(view.email).toEqual({ value: "invited@example.com", readOnly: true });
  await vi.waitFor(() => expect(body(view.calls, "/api/account-invitation-redemptions/v1")).toMatchObject({
    email: "invited@example.com", token: "invitation-token",
  }));
});

it.each([false, true])("registration (invited: %s) requires accepting the terms before creating an account", async (invited) => {
  const view = await submitRegistration({ invited, termsAccepted: false });

  expect(await screen.findByText("Zaakceptuj Regulamin, aby utworzyć konto.")).toBeTruthy();
  expect(view.registrations()).toEqual([]);
  expect(assign).not.toHaveBeenCalled();
});

it("public registration waits for email verification before entering the dashboard", async () => {
  const view = await submitRegistration({ verificationRequired: true });

  expect(await screen.findByRole("heading", { name: "Potwierdź adres e-mail" })).toBeTruthy();
  expect(view.registrations()).toEqual(["POST /api/tenant-registrations/v1"]);
  expect(assign).not.toHaveBeenCalled();
});

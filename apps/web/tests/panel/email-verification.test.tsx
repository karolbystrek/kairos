import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";

import { csrf, stubFetch } from "./helpers";

import { EmailVerification } from "@/components/email-verification";

const account = {
  accountId: "12345678-1234-4234-8234-123456789abc", email: "owner@example.com",
  tenantId: "12345678-1234-4234-8234-123456789abd", tenantRole: "ADMIN", assignment: null, capabilities: [],
};
let calls: ReturnType<typeof stubFetch>;
const props = { onVerified: vi.fn(), onChanged: vi.fn(), onCancelled: vi.fn() };

beforeEach(() => {
  calls = stubFetch((url) => {
    if (csrf(url)) return Response.json({ token: "csrf" });
    if (url.pathname === "/api/auth/v1/verification") return Response.json(account);

    return new Response(null, { status: 204 });
  });
  Object.values(props).forEach((mock) => mock.mockReset().mockResolvedValue(undefined));
});

function setup(cooldownSeconds = 60) {
  render(<EmailVerification
    verification={{ verificationRequired: true, email: "owner@example.com", resendAvailableAt: new Date(Date.now() + cooldownSeconds * 1000).toISOString() }}
    {...props}
  />);
}
const apiRequests = () => calls.filter(({ url }) => !csrf(url)).map(({ url }) => url.pathname);

it("verification rejects incomplete codes before sending a request", async () => {
  const user = userEvent.setup();

  setup();
  const input = screen.getByRole("textbox", { name: "Kod weryfikacyjny" });

  await user.type(input, "123");
  // The browser's pattern check stops the button; submit directly to reach the component's own guard.
  fireEvent.submit(input.closest("form")!);
  expect(screen.getByRole("status").textContent).toContain("sześciocyfrowy kod");
  expect(apiRequests()).toEqual([]);
});

it("six-digit verification completes authentication", async () => {
  const user = userEvent.setup();

  setup();
  await user.type(screen.getByRole("textbox", { name: "Kod weryfikacyjny" }), "123456");
  await user.click(screen.getByRole("button", { name: "Potwierdź e-mail" }));
  expect(apiRequests()).toEqual(["/api/auth/v1/verification"]);
  expect(JSON.parse(String(calls.at(-1)?.init.body))).toEqual({ code: "123456" });
  expect(props.onVerified).toHaveBeenCalledWith(account);
});

it("resend respects the server cooldown and change-email clears the server session", async () => {
  const user = userEvent.setup();

  setup(60);
  const resend = screen.getByRole("button", { name: "Wyślij ponownie za 60 s" });

  expect(resend.hasAttribute("disabled") || resend.getAttribute("aria-disabled") === "true").toBe(true);
  await user.click(screen.getByRole("button", { name: "Zmień adres e-mail / wyloguj się" }));
  expect(apiRequests()).toEqual(["/api/auth/v1/logout"]);
  expect(props.onCancelled).toHaveBeenCalledOnce();
});

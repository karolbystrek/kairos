import { waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";

import { renderWithSWR, stubFetch } from "./helpers";

import { LandingSessionRedirect } from "@/components/landing-session-redirect";
import { hasSessionHint, setSessionHint } from "@/src/api/session-hint";

const replace = vi.fn();

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

const account = {
  accountId: "12345678-1234-4234-8234-123456789abc",
  email: "staff@example.com",
  tenantId: "12345678-1234-4234-8234-123456789abd",
  tenantRole: "ADMIN", assignment: null, capabilities: [],
};

beforeEach(() => {
  replace.mockClear();
  setSessionHint(false);
});

it("anonymous visitors make no API call and stay on the landing page", async () => {
  const calls = stubFetch(() => new Response(null, { status: 401 }));

  renderWithSWR(<LandingSessionRedirect />);
  await new Promise((resolve) => setTimeout(resolve, 50));
  expect(calls).toEqual([]);
  expect(replace).not.toHaveBeenCalled();
});

it("returning staff are sent to the dashboard", async () => {
  setSessionHint(true);
  stubFetch(() => Response.json(account));

  renderWithSWR(<LandingSessionRedirect />);
  await waitFor(() => expect(replace).toHaveBeenCalledWith("/dashboard"));
});

it("an expired session clears the hint so the next visit is silent", async () => {
  setSessionHint(true);
  const calls = stubFetch(() => new Response(null, { status: 401 }));

  renderWithSWR(<LandingSessionRedirect />);
  await waitFor(() => expect(hasSessionHint()).toBe(false));
  expect(replace).not.toHaveBeenCalled();
  expect(calls).toHaveLength(1);
});

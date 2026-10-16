import { beforeEach, expect, it, vi } from "vitest";

import { csrf, stubFetch } from "./helpers";

import { apiFetch, initializeCsrf, resetCsrf } from "@/src/api/api-fetch";
import { subscribeToAuthenticationRequired } from "@/src/api/auth-state";
import * as authentication from "@/src/api/authentication";
import { registerTenant } from "@/src/api/tenant-registrations";

beforeEach(() => {
  resetCsrf();
  vi.spyOn(console, "error").mockImplementation(() => {});
});

it("expired sessions notify authentication listeners without request replay", async () => {
  const calls = stubFetch(() => new Response(null, { status: 401 }));
  const listener = vi.fn();
  const unsubscribe = subscribeToAuthenticationRequired(listener);

  try {
    await expect(apiFetch("/api/orders/v1")).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledOnce();
  } finally {
    unsubscribe();
  }
  expect(calls.map(({ url }) => url.pathname)).toEqual(["/api/orders/v1"]);
});

it.each([
  [401, 0],
  [409, 0],
  [429, 0],
  [500, 1],
])("status %i is logged %i time(s) as a technical error", async (status, logged) => {
  stubFetch(() => new Response(null, { status }));

  await expect(apiFetch("/api/orders/v1")).rejects.toMatchObject({ status });
  expect(console.error).toHaveBeenCalledTimes(logged);
});

it.each([true, false])("CSRF rejection retries once after retrieving a fresh token (recovers: %s)", async (recovered) => {
  let tokens = 0;
  let writes = 0;

  stubFetch((url, init) => {
    if (csrf(url)) return Response.json({ token: `csrf-${++tokens}` });
    expect(init.credentials).toBe("include");
    writes++;
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe(`csrf-${writes}`);
    if (writes === 1 || !recovered)
      return Response.json({ type: "urn:kairos:problem:csrf-token-invalid" }, { status: 403 });

    return new Response(null, { status: 204 });
  });
  const write = apiFetch("/api/orders/v1", { method: "POST" });

  if (recovered) await write;
  else await expect(write).rejects.toMatchObject({ status: 403 });
  expect(writes).toBe(2);
  expect(tokens).toBe(2);
});

const account = {
  accountId: "12345678-1234-4234-8234-123456789abc",
  email: "staff@example.com",
  tenantId: "12345678-1234-4234-8234-123456789abd",
  tenantRole: "ADMIN", assignment: null, capabilities: [],
};

it.each([
  ["login", () => authentication.login({ email: "staff@example.com", password: "password" }), account],
  ["logout", () => authentication.logout(), true],
  ["changePassword", () => authentication.changePassword("current", "replacement", "replacement"), undefined],
] as const)("%s keeps success when the next CSRF bootstrap is unavailable", async (_, operation, expected) => {
  let tokens = 0;
  let mutations = 0;

  stubFetch((url, init) => {
    if (csrf(url)) {
      if (++tokens === 2) throw new Error("Network unavailable");

      return Response.json({ token: `csrf-${tokens}` });
    }
    mutations++;
    if (url.pathname === "/api/orders/v1")
      expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("csrf-3");

    return url.pathname.endsWith("/login") ? Response.json(account) : new Response(null, { status: 204 });
  });
  await initializeCsrf();
  expect(await operation()).toEqual(expected);
  expect(tokens).toBe(1);
  expect(mutations).toBe(1);
  await expect(apiFetch("/api/orders/v1", { method: "POST" })).rejects.toThrow();
  expect(mutations).toBe(1);
  await apiFetch("/api/orders/v1", { method: "POST" });
  expect(mutations).toBe(2);
});

it("public registration rejects a short password before making a request", async () => {
  const calls = stubFetch(() => new Response(null, { status: 500 }));

  await expect(registerTenant({
    email: "admin@example.com", password: "short", passwordConfirmation: "short",
  })).rejects.toThrow(/12 znaków/i);
  expect(calls).toEqual([]);
});

it("public registration submits normalized account details without a location", async () => {
  const result = { ...account, email: "admin@example.com", capabilities: ["MANAGE_LOCATIONS"] };
  const calls = stubFetch((url) => csrf(url) ? Response.json({ token: "csrf" }) : Response.json(result));

  expect(await registerTenant({
    email: "  ADMIN@example.com  ", password: "password-12345", passwordConfirmation: "password-12345",
  })).toEqual(result);
  const submission = calls.find(({ url }) => url.pathname === "/api/tenant-registrations/v1");

  expect(JSON.parse(String(submission?.init.body))).toEqual({
    email: "admin@example.com", password: "password-12345", passwordConfirmation: "password-12345",
  });
});

it("registration field errors survive the API error boundary", async () => {
  stubFetch((url) => csrf(url)
    ? Response.json({ token: "csrf" })
    : Response.json({ status: 400, fieldErrors: { password: "Use at least 12 characters." } }, { status: 400 }));
  await expect(registerTenant({
    email: "admin@example.com", password: "password-12345", passwordConfirmation: "password-12345",
  })).rejects.toMatchObject({ problem: { fieldErrors: { password: "Use at least 12 characters." } } });
});

it("a stalled request times out as a quiet network error", async () => {
  // Timer-backed signals ignore fake timers, so the 10 s expiry is made immediate.
  const timeout = vi.spyOn(AbortSignal, "timeout")
    .mockImplementation(() => AbortSignal.abort(new DOMException("", "TimeoutError")));

  vi.stubGlobal("fetch", async (_: unknown, init: RequestInit) =>
    init.signal?.aborted ? Promise.reject(init.signal.reason) : new Promise(() => {}));
  await expect(apiFetch("/api/orders/v1")).rejects.toMatchObject({ name: "TimeoutError" });
  expect(timeout).toHaveBeenCalledWith(10_000);
  expect(console.error).not.toHaveBeenCalled();
  timeout.mockRestore();
});

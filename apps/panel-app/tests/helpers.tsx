import type { ReactNode } from "react";

import { fireEvent, render } from "@testing-library/react";
import { SWRConfig, useSWRConfig, type ScopedMutator } from "swr";
import { vi } from "vitest";

type Handler = (url: URL, init: RequestInit) => Response | Promise<Response>;

/** Replaces the network; every call is recorded with its parsed URL and init. */
export function stubFetch(handler: Handler) {
  const calls: { url: URL; init: RequestInit }[] = [];

  vi.stubGlobal("fetch", vi.fn(async (input: string | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), window.location.origin);

    calls.push({ url, init });

    return handler(url, init);
  }));

  return calls;
}

export const csrf = (url: URL) => url.pathname === "/api/auth/v1/csrf";

/** API calls other than CSRF bootstrap, as "METHOD /path". */
export function apiCalls(calls: ReturnType<typeof stubFetch>) {
  return calls.filter(({ url }) => !csrf(url)).map(({ url, init }) => `${init.method ?? "GET"} ${url.pathname}`);
}

/** JSON body of the latest request with a body sent to the path. */
export function body(calls: ReturnType<typeof stubFetch>, path: string) {
  const call = calls.findLast(({ url, init }) => url.pathname === path && init.body !== undefined);

  return call && JSON.parse(String(call.init.body));
}

/** jsdom cannot navigate documents, so full-page navigations are recorded instead. */
export function stubNavigation() {
  const assign = vi.fn();
  const real = window.location;

  vi.stubGlobal("location", {
    assign,
    get href() { return real.href; },
    get origin() { return real.origin; },
    get hash() { return real.hash; },
    set hash(value: string) { real.hash = value; },
  });

  return assign;
}

/** Renders with an isolated SWR cache; returns it with its mutate for driving revalidation. */
export function renderWithSWR(ui: ReactNode) {
  const cache = new Map();
  const swr = { mutate: undefined as unknown as ScopedMutator };
  function Capture() {
    swr.mutate = useSWRConfig().mutate;

    return null;
  }
  const wrapper = ({ children }: { children: ReactNode }) => (
    <SWRConfig value={{ provider: () => cache, dedupingInterval: 0 }}>
      <Capture />
      {children}
    </SWRConfig>
  );

  return { ...render(ui, { wrapper }), cache, swr };
}

type Route = (url: URL, init: RequestInit) => unknown;

/**
 * A fake API keyed by "METHOD /path". Plain values become JSON, a Response is
 * returned as is, and unknown routes answer 404. CSRF bootstrap always succeeds.
 */
export function fakeApi(routes: Record<string, Route>) {
  return stubFetch(async (url, init) => {
    if (csrf(url)) return Response.json({ token: "csrf" });
    const route = routes[`${init.method ?? "GET"} ${url.pathname}`];

    if (!route) return Response.json({ status: 404 }, { status: 404 });
    const result = await route(url, init);

    if (result instanceof Response) return result;

    return result === undefined ? new Response(null, { status: 204 }) : Response.json(result);
  });
}

export const status = (code: number) => () => Response.json({ status: code }, { status: code });

export function staffAccount(capabilities: string[] = [], overrides = {}) {
  return {
    accountId: "00000000-0000-4000-8000-0000000000a1", email: "staff@example.com",
    tenantId: "00000000-0000-4000-8000-0000000000b1", tenantRole: "ADMIN",
    assignment: null, capabilities, ...overrides,
  };
}

export function location(id: string, name = "Location", overrides = {}) {
  return {
    id, name, timeZone: "Europe/Warsaw", status: "ENABLED",
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
    googleReviewUrl: null, ...overrides,
  };
}

/** A promise resolved from the outside, for holding a request open. */
export function deferred<T = unknown>() {
  let resolve = (_: T) => {};
  const promise = new Promise<T>((settle) => { resolve = settle; });

  return { promise, resolve };
}

/** Confirms a hold-to-confirm button the way assistive technology does: two deliberate activations. */
export function confirmHold(button: HTMLElement) {
  fireEvent.click(button, { detail: 0 });
  fireEvent.click(button, { detail: 0 });
}

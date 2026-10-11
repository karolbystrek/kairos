import { afterEach, expect, it, vi } from "vitest";

vi.mock("node:fs", () => ({ readFileSync: vi.fn() }));
vi.mock("@serwist/next/config", () => ({ serwist: (options) => options }));

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

it("installs A, activates B and serves B's offline shell with its cached controls", async () => {
  vi.stubEnv("NODE_ENV", "production");
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.example");
  const location = new URL("https://customer.kairos.example/sw.js");
  const entries = new Map();
  const key = (request) => typeof request === "string" ? request : request.url;
  const cache = {
    match: async (request) => entries.get(key(request))?.clone(),
    put: async (request, response) => { entries.set(key(request), response.clone()); },
    keys: async () => [...entries.keys()].map((url) => new Request(url)),
    delete: async (request) => entries.delete(key(request)),
  };
  const caches = { open: async () => cache, match: cache.match };
  vi.stubGlobal("location", location);
  vi.stubGlobal("self", { location, caches, addEventListener: () => {} });
  vi.stubGlobal("caches", caches);
  vi.stubGlobal("FetchEvent", class FetchEvent {});
  vi.stubGlobal("ExtendableEvent", class ExtendableEvent {});
  const { Serwist, NavigationRoute, NetworkOnly } = await import("serwist");
  const { readFileSync } = await import("node:fs");
  let release;
  vi.stubGlobal("fetch", vi.fn(async (request) => {
    const url = new URL(key(request));
    return new Response(url.pathname === "/~offline"
      ? `<script src="/_next/static/${release}.js"></script><button>Retry</button>`
      : `controls-${release}`);
  }));
  const event = (type) => ({ type, waitUntil: () => {} });
  async function install(version) {
    release = version;
    readFileSync.mockReturnValue(version);
    vi.resetModules();
    const { default: config } = await import("../serwist.config.js");
    expect(readFileSync).toHaveBeenCalledWith(".next/BUILD_ID", "utf8");
    const worker = new Serwist({
      precacheEntries: [...config.additionalPrecacheEntries,
        { url: `/_next/static/${version}.js`, revision: null }],
    });
    worker.registerRoute(new NavigationRoute(new NetworkOnly()));
    worker.setCatchHandler(async () => worker.matchPrecache("/~offline"));
    await worker.handleInstall(event("install"));
    await worker.handleActivate(event("activate"));
    return worker;
  }
  const a = await install("A");
  expect(await (await a.matchPrecache("/~offline")).text()).toContain("/A.js");
  const b = await install("B");
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
  const navigation = new Request("https://customer.kairos.example/orders/saved");
  Object.defineProperty(navigation, "mode", { value: "navigate" });
  const shell = await (await b.handleRequest({ request: navigation, event: event("fetch") })).text();
  expect(shell).toContain("/B.js");
  expect(shell).toContain("<button>Retry</button>");
  expect(await (await b.matchPrecache("/_next/static/B.js")).text()).toBe("controls-B");
  expect(await b.matchPrecache("/_next/static/A.js")).toBeUndefined();
  expect([...entries.keys()].some((url) => url.includes("REVISION__=A"))).toBe(false);
});

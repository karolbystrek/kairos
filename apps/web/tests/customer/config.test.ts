import { expect, it, vi } from "vitest";

import { paths } from "@/src/routes";

vi.mock("node:fs", () => ({ readFileSync: () => "release-b\n" }));
vi.mock("@serwist/next/config", () => ({ serwist: (options: unknown) => options }));

it("revisions the precached offline shell with each release's build ID", async () => {
  const config = (await import("../../serwist.config.js")).default as unknown as {
    additionalPrecacheEntries: unknown[];
  };

  expect(config.additionalPrecacheEntries).toContainEqual({
    url: paths.offlineFallback,
    revision: "release-b",
  });
});

it("restricts the worker to same-origin precaching and API requests", async () => {
  const { default: config } = await import("../../next.config.mjs");
  const routes = (await config("phase-production-server").headers?.()) ?? [];
  const policy = routes
    .find(({ source }) => source === paths.serviceWorker)
    ?.headers.find(({ key }) => key === "Content-Security-Policy")?.value;
  const connections = policy
    ?.split(";")
    .map((directive) => directive.trim().split(/\s+/))
    .find(([directive]) => directive === "connect-src")
    ?.slice(1);

  expect(connections).toEqual(["'self'"]);
});

it("serves the worker uncached and allows only the customer scope", async () => {
  const { default: config } = await import("../../next.config.mjs");
  const routes = (await config("phase-production-server").headers?.()) ?? [];
  const headers = new Map(
    routes
      .find(({ source }) => source === paths.serviceWorker)
      ?.headers.map(({ key, value }) => [key, value]),
  );

  expect(headers.get("Cache-Control")).toBe("no-cache, no-store, must-revalidate");
  expect(headers.get("Service-Worker-Allowed")).toBe(paths.serviceWorkerScope);
});

import { expect, it, vi } from "vitest";

vi.mock("node:fs", () => ({ readFileSync: () => "release-b\n" }));
vi.mock("@serwist/next/config", () => ({ serwist: (options: unknown) => options }));

it("revisions the precached offline shell with each release's build ID", async () => {
  const config = (await import("../serwist.config.js")).default as unknown as {
    additionalPrecacheEntries: unknown[];
  };

  expect(config.additionalPrecacheEntries).toContainEqual({
    url: "/~offline",
    revision: "release-b",
  });
});

it("restricts the worker to same-origin precaching and API requests", async () => {
  const { default: config } = await import("../next.config.mjs");
  const routes = (await config("phase-production-server").headers?.()) ?? [];
  const policy = routes
    .find(({ source }) => source === "/sw.js")
    ?.headers.find(({ key }) => key === "Content-Security-Policy")?.value;
  const connections = policy
    ?.split(";")
    .map((directive) => directive.trim().split(/\s+/))
    .find(([directive]) => directive === "connect-src")
    ?.slice(1);

  expect(connections).toEqual(["'self'"]);
});

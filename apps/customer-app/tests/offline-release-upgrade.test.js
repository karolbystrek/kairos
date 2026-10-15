import { expect, it, vi } from "vitest";

vi.mock("node:fs", () => ({ readFileSync: () => "release-b\n" }));
vi.mock("@serwist/next/config", () => ({ serwist: (options) => options }));

it("revisions the precached offline shell with each release's build ID", async () => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.example");
  const { default: config } = await import("../serwist.config.js");

  expect(config.additionalPrecacheEntries).toContainEqual({
    url: "/~offline",
    revision: "release-b",
  });
  vi.unstubAllEnvs();
});

import { afterEach, expect, it, vi } from "vitest";

afterEach(() => vi.unstubAllEnvs());

it("allows worker precaching and API requests while restricting other origins", async () => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.example");
  const { default: config } = await import("../next.config.mjs");
  const routes = await config.headers();
  const policy = routes
    .find(({ source }) => source === "/sw.js")
    ?.headers.find(({ key }) => key === "Content-Security-Policy")?.value;
  const connections = policy
    ?.split(";")
    .map((directive) => directive.trim().split(/\s+/))
    .find(([directive]) => directive === "connect-src")
    ?.slice(1);

  expect(connections).toEqual(["'self'", "https://api.kairos.example"]);
});

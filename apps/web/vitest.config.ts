import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL(".", import.meta.url)),
    },
  },
  test: {
    projects: [
      {
        extends: true,
        test: {
          name: "customer",
          environment: "node",
          include: ["tests/*.test.ts", "tests/customer/**/*.test.{ts,tsx}"],
        },
      },
      {
        extends: true,
        test: {
          name: "panel",
          environment: "jsdom",
          include: ["tests/panel/**/*.test.{ts,tsx}"],
          setupFiles: ["./tests/panel/setup.ts"],
        },
      },
    ],
  },
});

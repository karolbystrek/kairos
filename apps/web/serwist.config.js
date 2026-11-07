import { readFileSync } from "node:fs";

import { serwist } from "@serwist/next/config";

// Served from /orders/ so the worker only ever controls customer pages, never /panel or /.
export default serwist({
  additionalPrecacheEntries: [
    {
      url: "/orders/~offline",
      revision: readFileSync(".next/BUILD_ID", "utf8").trim(),
    },
  ],
  precachePrerendered: false,
  swDest: "public/orders/sw.js",
  swSrc: "app/sw.ts",
});

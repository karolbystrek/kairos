import { readFileSync } from "node:fs";

import { serwist } from "@serwist/next/config";

// Served from /o/ so the worker's default scope is the customer app, never /panel or /.
export default serwist({
  additionalPrecacheEntries: [
    {
      url: "/o/~offline",
      revision: readFileSync(".next/BUILD_ID", "utf8").trim(),
    },
  ],
  precachePrerendered: false,
  swDest: "public/o/sw.js",
  swSrc: "app/sw.ts",
});

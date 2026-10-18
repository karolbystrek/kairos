import { readFileSync } from "node:fs";

import { serwist } from "@serwist/next/config";

export default serwist({
  additionalPrecacheEntries: [
    {
      url: "/~offline",
      revision: readFileSync(".next/BUILD_ID", "utf8").trim(),
    },
  ],
  precachePrerendered: false,
  swDest: "public/sw.js",
  swSrc: "app/sw.ts",
});

import { serwist } from "@serwist/next/config";

export default serwist({
  additionalPrecacheEntries: [
    {
      url: "/~offline",
      revision: "kairos-offline-shell-v1",
    },
  ],
  precachePrerendered: false,
  swDest: "public/sw.js",
  swSrc: "app/sw.ts",
});

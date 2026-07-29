import { serwist } from "@serwist/next/config";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

if (!apiBaseUrl) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured.");
}

export default serwist({
  additionalPrecacheEntries: [
    {
      url: "/~offline",
      revision: "kairos-offline-shell-v1",
    },
  ],
  precachePrerendered: false,
  esbuildOptions: {
    define: {
      "process.env.NEXT_PUBLIC_API_BASE_URL": JSON.stringify(apiBaseUrl),
    },
  },
  swDest: "public/sw.js",
  swSrc: "app/sw.ts",
});

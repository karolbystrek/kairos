import { PHASE_DEVELOPMENT_SERVER } from "next/constants.js";

/** @type {(phase: string) => import('next').NextConfig} */
const nextConfig = (phase) => ({
  output: "standalone",
  poweredByHeader: false,
  experimental: {
    // A stale persistent cache made Turbopack spawn PostCSS workers without bound and exhaust RAM.
    turbopackFileSystemCacheForDev: false,
  },
  // Production NGINX routes /api; `next dev` has no gateway, so it proxies to the API itself.
  ...(phase === PHASE_DEVELOPMENT_SERVER && {
    rewrites: async () => [
      {
        source: "/api/:path*",
        destination: `${process.env.API_PROXY_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ],
  }),
});

export default nextConfig;

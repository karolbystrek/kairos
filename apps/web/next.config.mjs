import { PHASE_DEVELOPMENT_SERVER } from "next/constants.js";

/** @type {(phase: string) => import('next').NextConfig} */
const nextConfig = (phase) => ({
  output: "standalone",
  poweredByHeader: false,
  // The customer launcher lives at /o/ so the /o/ service worker scope covers it.
  skipTrailingSlashRedirect: true,
  experimental: {
    // Customer and panel pages have separate root layouts, so unmatched URLs need their own page.
    globalNotFound: true,
    // A stale persistent cache made Turbopack spawn PostCSS workers without bound and exhaust RAM.
    turbopackFileSystemCacheForDev: false,
  },
  async headers() {
    return [
      {
        source: "/o/:path+",
        headers: [{ key: "X-Robots-Tag", value: "noindex, nofollow" }],
      },
      {
        source: "/o/sw.js",
        headers: [
          {
            key: "Cache-Control",
            value: "no-cache, no-store, must-revalidate",
          },
          {
            key: "Content-Security-Policy",
            value: "default-src 'self'; script-src 'self'; connect-src 'self'",
          },
          {
            key: "Content-Type",
            value: "application/javascript; charset=utf-8",
          },
          {
            key: "X-Content-Type-Options",
            value: "nosniff",
          },
        ],
      },
    ];
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

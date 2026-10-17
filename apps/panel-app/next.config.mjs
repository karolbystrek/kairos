for (const name of [
  "NEXT_PUBLIC_API_BASE_URL",
  "NEXT_PUBLIC_CUSTOMER_APP_URL",
  "NEXT_PUBLIC_PANEL_APP_URL",
]) {
  if (!process.env[name]) throw new Error(`${name} is not configured.`);
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  poweredByHeader: false,
  experimental: {
    // A stale persistent cache made Turbopack spawn PostCSS workers without bound and exhaust RAM.
    turbopackFileSystemCacheForDev: false,
  },
};

export default nextConfig;

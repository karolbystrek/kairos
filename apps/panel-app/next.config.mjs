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

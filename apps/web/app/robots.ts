import type { MetadataRoute } from "next";

import { appUrl } from "@/src/config/app-url";

// Private pages stay crawlable so crawlers can read their noindex rules.
export default async function robots(): Promise<MetadataRoute.Robots> {
  const siteUrl = await appUrl();

  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: new URL("/sitemap.xml", siteUrl).toString(),
  };
}

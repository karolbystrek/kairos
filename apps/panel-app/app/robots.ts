import type { MetadataRoute } from "next";

import { panelAppUrl } from "@/src/config/site-urls";

// Private pages stay crawlable so crawlers can read their noindex rules.
export default async function robots(): Promise<MetadataRoute.Robots> {
  const siteUrl = await panelAppUrl();

  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: new URL("/sitemap.xml", siteUrl).toString(),
  };
}

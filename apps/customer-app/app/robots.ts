import type { MetadataRoute } from "next";

import { customerAppUrl } from "@/src/config/customer-app-url";

// Private pages stay crawlable so crawlers can read their noindex rules.
export default async function robots(): Promise<MetadataRoute.Robots> {
  const siteUrl = await customerAppUrl();

  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: new URL("/sitemap.xml", siteUrl).toString(),
  };
}

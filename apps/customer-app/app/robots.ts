import type { MetadataRoute } from "next";

import { customerAppUrl } from "@/src/config/customer-app-url";

// Private pages stay crawlable so crawlers can read their noindex rules.
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: new URL("/sitemap.xml", customerAppUrl).toString(),
  };
}

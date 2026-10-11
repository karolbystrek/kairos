import type { MetadataRoute } from "next";

import { panelAppUrl } from "@/src/config/public-environment";

// Private pages stay crawlable so crawlers can read their noindex rules.
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: new URL("/sitemap.xml", panelAppUrl).toString(),
  };
}

import type { MetadataRoute } from "next";

import { customerAppUrl } from "@/src/config/customer-app-url";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const siteUrl = await customerAppUrl();

  return ["/", "/prywatnosc"].map((path) => ({
    url: new URL(path, siteUrl).toString(),
  }));
}

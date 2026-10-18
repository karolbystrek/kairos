import type { MetadataRoute } from "next";

import { panelAppUrl } from "@/src/config/site-urls";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const siteUrl = await panelAppUrl();

  return ["/", "/regulamin", "/polityka-prywatnosci"].map((path) => ({
    url: new URL(path, siteUrl).toString(),
  }));
}

import type { MetadataRoute } from "next";

import { panelAppUrl } from "@/src/config/public-environment";

export default function sitemap(): MetadataRoute.Sitemap {
  return ["/", "/regulamin", "/polityka-prywatnosci"].map((path) => ({
    url: new URL(path, panelAppUrl).toString(),
  }));
}

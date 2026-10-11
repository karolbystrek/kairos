import type { MetadataRoute } from "next";

import { customerAppUrl } from "@/src/config/customer-app-url";

export default function sitemap(): MetadataRoute.Sitemap {
  return ["/", "/prywatnosc"].map((path) => ({
    url: new URL(path, customerAppUrl).toString(),
  }));
}

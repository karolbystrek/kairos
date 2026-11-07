import type { MetadataRoute } from "next";

import { appUrl } from "@/src/config/app-url";
import { paths } from "@/src/routes";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const siteUrl = await appUrl();

  return [
    paths.home,
    paths.terms,
    paths.privacy,
    paths.panelPrivacy,
    paths.customerHome,
  ].map((path) => ({
    url: new URL(path, siteUrl).toString(),
  }));
}

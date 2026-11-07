import type { MetadataRoute } from "next";

import { siteConfig } from "@/config/site";
import { paths } from "@/src/routes";

export const PWA_THEME_COLOR = "#ffffff";

const icons: NonNullable<MetadataRoute.Manifest["icons"]> = [
  {
    src: "/icons/kairos-icon-192.png",
    sizes: "192x192",
    type: "image/png",
    purpose: "any",
  },
  {
    src: "/icons/kairos-icon-512.png",
    sizes: "512x512",
    type: "image/png",
    purpose: "any",
  },
  {
    src: "/icons/kairos-icon-maskable-512.png",
    sizes: "512x512",
    type: "image/png",
    purpose: "maskable",
  },
];

export function createKairosManifest(startUrl: string): MetadataRoute.Manifest {
  return {
    name: siteConfig.customer.name,
    short_name: "Kairos",
    lang: "pl",
    description: siteConfig.customer.description,
    id: paths.serviceWorkerScope,
    start_url: startUrl,
    scope: paths.serviceWorkerScope,
    display: "standalone",
    launch_handler: { client_mode: ["navigate-existing", "auto"] },
    background_color: PWA_THEME_COLOR,
    theme_color: PWA_THEME_COLOR,
    icons,
  };
}

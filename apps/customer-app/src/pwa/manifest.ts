import type { MetadataRoute } from "next";

import { siteConfig } from "@/config/site";

export const INSTALL_ORDER_QUERY_PARAMETER = "install-order";
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
    name: siteConfig.name,
    short_name: "Kairos",
    description: siteConfig.description,
    id: "/",
    start_url: startUrl,
    scope: "/",
    display: "standalone",
    background_color: PWA_THEME_COLOR,
    theme_color: PWA_THEME_COLOR,
    icons,
  };
}

export function getInstallationStartUrl(trackingReference: string): string {
  const searchParams = new URLSearchParams({
    [INSTALL_ORDER_QUERY_PARAMETER]: trackingReference,
  });

  return `/?${searchParams.toString()}`;
}

export function getOrderManifestUrl(trackingReference: string): string {
  return `/manifests/orders/${encodeURIComponent(trackingReference)}`;
}

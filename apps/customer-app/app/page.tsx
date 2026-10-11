import type { Metadata } from "next";

import { CustomerHome } from "./customer-home";

import { siteConfig } from "@/config/site";
import { INSTALL_ORDER_QUERY_PARAMETER } from "@/src/pwa/manifest";
import { publicPageMetadata } from "@/src/seo/metadata";

export const metadata: Metadata = {
  ...publicPageMetadata({
    title: siteConfig.name,
    description: siteConfig.description,
    path: "/",
  }),
  title: { absolute: siteConfig.name },
};

export default async function Home({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = await searchParams;
  const installationOrderValue =
    resolvedSearchParams[INSTALL_ORDER_QUERY_PARAMETER];
  const installationTrackingReference = Array.isArray(installationOrderValue)
    ? (installationOrderValue[0] ?? null)
    : (installationOrderValue ?? null);

  return (
    <CustomerHome
      installationTrackingReference={installationTrackingReference}
    />
  );
}

import type { Metadata } from "next";

import { OrderTracker } from "./order-tracker";

import { getOrderManifestUrl } from "@/src/pwa/manifest";
import { NOT_INDEXED, SHARED_OPEN_GRAPH } from "@/src/seo/metadata";

const TITLE = "Śledzenie zamówienia";
const DESCRIPTION = "Sprawdź aktualny status zamówienia w restauracji.";

type TrackedOrderPageProps = {
  params: Promise<{ trackingReference: string }>;
};

export async function generateMetadata({
  params,
}: TrackedOrderPageProps): Promise<Metadata> {
  const { trackingReference } = await params;

  // Shared order links must not reveal the tracking reference or status.
  return {
    title: TITLE,
    description: DESCRIPTION,
    manifest: getOrderManifestUrl(trackingReference),
    robots: NOT_INDEXED,
    openGraph: { ...SHARED_OPEN_GRAPH, title: TITLE, description: DESCRIPTION },
  };
}

export default async function TrackedOrderPage({
  params,
}: TrackedOrderPageProps) {
  const { trackingReference } = await params;

  return (
    <OrderTracker
      key={trackingReference}
      trackingReference={trackingReference}
    />
  );
}

import type { Metadata } from "next";

import { OrderTracker } from "./order-tracker";

import { getOrderManifestUrl } from "@/src/pwa/manifest";

type TrackedOrderPageProps = {
  params: Promise<{ trackingReference: string }>;
};

export async function generateMetadata({
  params,
}: TrackedOrderPageProps): Promise<Metadata> {
  const { trackingReference } = await params;

  return {
    manifest: getOrderManifestUrl(trackingReference),
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

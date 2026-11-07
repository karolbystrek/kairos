import type { Metadata } from "next";

import { OfflineNavigationFallback } from "./offline-navigation-fallback";

import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = { robots: NOT_INDEXED };

export default function OfflineFallbackPage() {
  return <OfflineNavigationFallback />;
}

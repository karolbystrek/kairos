import type { Metadata } from "next";

import { Suspense } from "react";

import { CustomerHomeFromUrl } from "./customer-home";

import { siteConfig } from "@/config/site";
import { publicPageMetadata } from "@/src/seo/metadata";

export const metadata: Metadata = {
  ...publicPageMetadata({
    title: siteConfig.name,
    description: siteConfig.description,
    path: "/",
  }),
  title: { absolute: siteConfig.name },
};

export default function Home() {
  return (
    <Suspense>
      <CustomerHomeFromUrl />
    </Suspense>
  );
}

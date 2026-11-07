import type { Metadata } from "next";

import { Suspense } from "react";

import { CustomerHomeFromUrl } from "./customer-home";

import { siteConfig } from "@/config/site";
import { paths } from "@/src/routes";
import { publicPageMetadata } from "@/src/seo/metadata";

export const metadata: Metadata = {
  ...publicPageMetadata({
    title: siteConfig.customer.name,
    description: siteConfig.customer.description,
    path: paths.customerHome,
  }),
  title: { absolute: siteConfig.customer.name },
};

export default function Home() {
  return (
    <Suspense>
      <CustomerHomeFromUrl />
    </Suspense>
  );
}

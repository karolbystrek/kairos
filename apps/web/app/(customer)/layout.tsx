import { Metadata, Viewport } from "next";

import { siteConfig } from "@/config/site";
import { PWA_THEME_COLOR } from "@/src/pwa/manifest";
import { CustomerPwaProvider } from "@/src/pwa/notification-provider";
import { ReviewInvitation } from "@/src/pwa/review-invitation";
import { paths } from "@/src/routes";
import { SHARED_OPEN_GRAPH } from "@/src/seo/metadata";

export const metadata: Metadata = {
  title: {
    default: siteConfig.customer.name,
    template: `%s - ${siteConfig.customer.name}`,
  },
  description: siteConfig.customer.description,
  manifest: paths.customerManifest,
  openGraph: {
    ...SHARED_OPEN_GRAPH,
    title: siteConfig.customer.name,
    description: siteConfig.customer.description,
  },
  icons: {
    icon: "/favicon.ico",
    apple: [
      {
        url: "/apple-touch-icon.png",
        sizes: "180x180",
        type: "image/png",
      },
    ],
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "Kairos",
  },
};

export const viewport: Viewport = {
  colorScheme: "light dark",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: PWA_THEME_COLOR },
    { media: "(prefers-color-scheme: dark)", color: "#000000" },
  ],
};

export default function CustomerLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <CustomerPwaProvider>
      <main className="mx-auto flex w-full flex-1 flex-col max-w-[var(--content-customer)] px-5 pb-[max(2.5rem,env(safe-area-inset-bottom))] pt-[max(1.25rem,env(safe-area-inset-top))] sm:px-6 sm:pt-6">
        {children}
      </main>
      <ReviewInvitation />
    </CustomerPwaProvider>
  );
}

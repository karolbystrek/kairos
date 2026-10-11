import "@/styles/globals.css";
import type { Metadata, Viewport } from "next";

import { Providers } from "./providers";

import { siteConfig } from "@/config/site";
import { panelAppUrl } from "@/src/config/public-environment";
import { SHARED_OPEN_GRAPH } from "@/src/seo/metadata";

export const metadata: Metadata = {
  metadataBase: new URL(panelAppUrl),
  applicationName: "Kairos",
  title: {
    default: siteConfig.name,
    template: `%s - ${siteConfig.name}`,
  },
  description: siteConfig.description,
  openGraph: {
    ...SHARED_OPEN_GRAPH,
    title: siteConfig.name,
    description: siteConfig.description,
  },
  twitter: { card: "summary_large_image" },
  icons: {
    icon: "/favicon.ico",
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#000000" },
  ],
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html suppressHydrationWarning lang="pl">
      <body className="min-h-screen bg-background text-foreground antialiased">
        <Providers
          themeProps={{
            attribute: "class",
            defaultTheme: "system",
            enableSystem: true,
          }}
        >
          <main className="panel-page mx-auto min-h-screen max-w-[var(--content-panel)] px-5 pb-24 pt-5 sm:px-7 sm:pb-10 sm:pt-6 lg:px-10">
            {children}
          </main>
        </Providers>
      </body>
    </html>
  );
}

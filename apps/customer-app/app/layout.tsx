import "@/styles/globals.css";
import { Metadata, Viewport } from "next";

import { Providers } from "./providers";

import { siteConfig } from "@/config/site";
import { customerAppUrl } from "@/src/config/customer-app-url";
import { PWA_THEME_COLOR } from "@/src/pwa/manifest";
import { SHARED_OPEN_GRAPH } from "@/src/seo/metadata";

export const metadata: Metadata = {
  metadataBase: customerAppUrl,
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

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html suppressHydrationWarning lang="pl">
      <head />
      <body className="flex min-h-dvh flex-col bg-background text-foreground antialiased">
        <Providers
          themeProps={{
            attribute: "class",
            defaultTheme: "system",
            enableSystem: true,
          }}
        >
          <main className="mx-auto flex w-full flex-1 flex-col max-w-[var(--content-customer)] px-5 pb-[max(2.5rem,env(safe-area-inset-bottom))] pt-[max(1.25rem,env(safe-area-inset-top))] sm:px-6 sm:pt-6">
            {children}
          </main>
        </Providers>
      </body>
    </html>
  );
}

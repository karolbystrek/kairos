import "@/styles/globals.css";
import type { Metadata, Viewport } from "next";

import { Providers } from "./providers";

import { siteConfig } from "@/config/site";
import { appUrl } from "@/src/config/app-url";
import { SHARED_OPEN_GRAPH } from "@/src/seo/metadata";

export const generateMetadata = async (): Promise<Metadata> => ({
  metadataBase: await appUrl(),
  applicationName: "Kairos",
  title: {
    default: siteConfig.panel.name,
    template: `%s - ${siteConfig.panel.name}`,
  },
  description: siteConfig.panel.description,
  openGraph: {
    ...SHARED_OPEN_GRAPH,
    title: siteConfig.panel.name,
    description: siteConfig.panel.description,
  },
  twitter: { card: "summary_large_image" },
  icons: {
    icon: "/favicon.ico",
  },
});

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#000000" },
  ],
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html suppressHydrationWarning lang="pl">
      <body className="flex min-h-dvh flex-col bg-background text-foreground antialiased">
        <Providers
          appUrl={(await appUrl()).origin}
          themeProps={{
            attribute: "class",
            defaultTheme: "system",
            enableSystem: true,
          }}
        >
          {children}
        </Providers>
      </body>
    </html>
  );
}

import "@/styles/panel.css";
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
          <main className="panel-page mx-auto flex w-full flex-1 flex-col max-w-[var(--content-panel)] px-5 pb-24 pt-5 sm:px-7 sm:pb-10 sm:pt-6 lg:px-10">
            {children}
          </main>
        </Providers>
      </body>
    </html>
  );
}

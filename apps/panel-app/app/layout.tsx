import "@/styles/globals.css";
import type { Metadata, Viewport } from "next";

import { Providers } from "./providers";

import { siteConfig } from "@/config/site";
import { customerAppUrl, panelAppUrl } from "@/src/config/site-urls";
import { SHARED_OPEN_GRAPH } from "@/src/seo/metadata";

export const generateMetadata = async (): Promise<Metadata> => ({
  metadataBase: new URL(await panelAppUrl()),
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
          customerAppUrl={await customerAppUrl()}
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

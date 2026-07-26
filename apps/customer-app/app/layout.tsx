import "@/styles/globals.css";
import { Metadata, Viewport } from "next";

import { Providers } from "./providers";

import { siteConfig } from "@/config/site";
import { PWA_THEME_COLOR } from "@/src/pwa/manifest";

export const metadata: Metadata = {
  title: {
    default: siteConfig.name,
    template: `%s - ${siteConfig.name}`,
  },
  description: siteConfig.description,
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
  colorScheme: "light",
  themeColor: PWA_THEME_COLOR,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html suppressHydrationWarning lang="en">
      <head />
      <body className="min-h-screen bg-background text-foreground antialiased">
        <Providers
          themeProps={{
            attribute: "class",
            defaultTheme: "light",
            enableSystem: false,
            forcedTheme: "light",
          }}
        >
          <main className="mx-auto max-w-xl px-6 py-10">{children}</main>
        </Providers>
      </body>
    </html>
  );
}

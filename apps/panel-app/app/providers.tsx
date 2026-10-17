"use client";

import type { ThemeProviderProps } from "next-themes";

import * as React from "react";
import { I18nProvider, ToastProvider } from "@heroui/react";
import { IconContext } from "@phosphor-icons/react";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import { SWRConfig, type SWRConfiguration } from "swr";

import { ApiError } from "@/src/api/api-fetch";

interface ProvidersProps {
  children: React.ReactNode;
  themeProps?: ThemeProviderProps;
}

// Icons are decorative by default; controls carry the accessible name.
const iconDefaults = { "aria-hidden": true } as const;

// Client errors will not succeed on retry; other failures retry a few times.
const swrDefaults: SWRConfiguration = {
  dedupingInterval: 10_000,
  errorRetryCount: 3,
  shouldRetryOnError: (error) =>
    !(error instanceof ApiError && error.status >= 400 && error.status < 500),
};

// Matches the breakpoint where the tab bar moves from the bottom into the header.
const wideQuery = "(min-width: 40rem)";

function subscribeToWidth(listener: () => void) {
  const query = window.matchMedia(wideQuery);

  query.addEventListener("change", listener);

  return () => query.removeEventListener("change", listener);
}

// Toasts sit on the side of the screen opposite the tab bar.
function useToastPlacement() {
  const isWide = React.useSyncExternalStore(
    subscribeToWidth,
    () => window.matchMedia(wideQuery).matches,
    () => true,
  );

  return isWide ? "bottom" : "top";
}

export function Providers({ children, themeProps }: ProvidersProps) {
  const toastPlacement = useToastPlacement();

  return (
    <I18nProvider locale="pl-PL">
      <IconContext.Provider value={iconDefaults}>
        <NextThemesProvider {...themeProps}>
          <SWRConfig value={swrDefaults}>{children}</SWRConfig>
          <ToastProvider placement={toastPlacement} />
        </NextThemesProvider>
      </IconContext.Provider>
    </I18nProvider>
  );
}

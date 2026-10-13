"use client";

import type { ThemeProviderProps } from "next-themes";

import * as React from "react";
import { I18nProvider } from "@heroui/react";
import { IconContext } from "@phosphor-icons/react";
import { ThemeProvider as NextThemesProvider } from "next-themes";

export interface ProvidersProps {
  children: React.ReactNode;
  themeProps?: ThemeProviderProps;
}

// Icons are decorative by default; controls carry the accessible name.
const iconDefaults = { "aria-hidden": true } as const;

export function Providers({ children, themeProps }: ProvidersProps) {
  return (
    <I18nProvider locale="pl-PL">
      <IconContext.Provider value={iconDefaults}>
        <NextThemesProvider {...themeProps}>{children}</NextThemesProvider>
      </IconContext.Provider>
    </I18nProvider>
  );
}

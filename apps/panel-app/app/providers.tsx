"use client";

import type { ThemeProviderProps } from "next-themes";

import * as React from "react";
import { I18nProvider } from "@heroui/react";
import { ThemeProvider as NextThemesProvider } from "next-themes";

export interface ProvidersProps {
  children: React.ReactNode;
  themeProps?: ThemeProviderProps;
}

export function Providers({ children, themeProps }: ProvidersProps) {
  return (
    <I18nProvider locale="pl-PL">
      <NextThemesProvider {...themeProps}>{children}</NextThemesProvider>
    </I18nProvider>
  );
}

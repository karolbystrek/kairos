"use client";

import type { ThemeProviderProps } from "next-themes";

import * as React from "react";
import { ThemeProvider as NextThemesProvider } from "next-themes";

import { ReviewInvitation } from "@/src/pwa/review-invitation";
import { CustomerPwaProvider } from "@/src/pwa/notification-provider";

export interface ProvidersProps {
  children: React.ReactNode;
  themeProps?: ThemeProviderProps;
}

export function Providers({ children, themeProps }: ProvidersProps) {
  return (
    <NextThemesProvider {...themeProps}>
      <CustomerPwaProvider>
        {children}
        <ReviewInvitation />
      </CustomerPwaProvider>
    </NextThemesProvider>
  );
}

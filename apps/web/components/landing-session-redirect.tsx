"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import useSWR from "swr";

import { getCurrentAccount } from "@/src/api/authentication";
import { currentAccountKey } from "@/src/api/cache-keys";
import { hasSessionHint } from "@/src/api/session-hint";
import { defaultPanelRoute, panelPath } from "@/src/routes";

export function LandingSessionRedirect() {
  const router = useRouter();
  // Anonymous visitors have no hint and make no API call.
  const maybeSignedIn = useSyncExternalStore(
    () => () => {},
    hasSessionHint,
    () => false,
  );
  const { data: account, error } = useSWR(
    maybeSignedIn ? currentAccountKey : null,
    getCurrentAccount,
    { shouldRetryOnError: false },
  );

  useEffect(() => {
    if (account && !error) router.replace(panelPath(defaultPanelRoute));
  }, [account, error, router]);

  return null;
}

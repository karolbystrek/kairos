"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import useSWR from "swr";

import { getCurrentAccount } from "@/src/api/authentication";
import { currentAccountKey } from "@/src/api/cache-keys";

export function LandingSessionRedirect() {
  const router = useRouter();
  const { data: account, error } = useSWR(
    currentAccountKey,
    getCurrentAccount,
    { shouldRetryOnError: false },
  );

  useEffect(() => {
    if (account && !error) router.replace("/dashboard");
  }, [account, error, router]);

  return null;
}

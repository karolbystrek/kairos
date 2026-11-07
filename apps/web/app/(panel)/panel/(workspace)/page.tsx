"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { defaultPanelRoute, panelPath } from "@/src/routes";

export default function PanelHome() {
  const router = useRouter();

  useEffect(() => {
    router.replace(panelPath(defaultPanelRoute));
  }, [router]);

  return null;
}

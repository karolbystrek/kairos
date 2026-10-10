"use client";

import { Button, Spinner } from "@heroui/react";
import { QrCode } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { CustomerToolbar } from "@/components/customer-toolbar";
import { resolveCustomerLaunchHref } from "@/src/pwa/launch-destination";
import { consumeInstallationBootstrap } from "@/src/pwa/recently-tracked-orders";
import { readLastStableDestination } from "@/src/pwa/storage";

function isStandaloneDisplayMode(): boolean {
  const navigatorWithStandalone = navigator as Navigator & {
    standalone?: boolean;
  };

  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    navigatorWithStandalone.standalone === true
  );
}

export function CustomerHome({
  installationTrackingReference,
}: {
  installationTrackingReference: string | null;
}) {
  const router = useRouter();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    let active = true;

    void readLastStableDestination().then((lastDestination) => {
      if (!active) {
        return;
      }
      const launchHref = resolveCustomerLaunchHref({
        installationTrackingReference: isStandaloneDisplayMode()
          ? consumeInstallationBootstrap(installationTrackingReference)
          : null,
        lastDestination,
      });

      if (launchHref !== "/") {
        router.replace(launchHref);

        return;
      }
      if (installationTrackingReference) {
        router.replace("/");
      }
      setIsReady(true);
    });

    return () => {
      active = false;
    };
  }, [installationTrackingReference, router]);

  return (
    <section className="relative min-h-[calc(100svh-3rem)]">
      <CustomerToolbar className="absolute right-0 top-0 z-10" />
      <div className="flex min-h-[calc(100svh-3rem)] items-center justify-center">
        {isReady ? (
          <Button
            aria-label="Scan QR code"
            className="empty-scan-action"
            variant="tertiary"
            onPress={() => router.push("/scan")}
          >
            <span className="empty-scan-action-label">Scan QR code</span>
            <QrCode className="empty-scan-action-icon" size={128} />
          </Button>
        ) : (
          <Spinner aria-label="Opening Kairos" />
        )}
      </div>
    </section>
  );
}

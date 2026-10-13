"use client";

import { Button, Link, Spinner } from "@heroui/react";
import { QrCodeIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { CustomerToolbar } from "@/components/customer-toolbar";
import { resolveCustomerLaunchHref } from "@/src/pwa/launch-destination";
import { isAppleMobile } from "@/src/pwa/notification-onboarding";
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
  const [showScanInAppHint, setShowScanInAppHint] = useState(false);

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
      setShowScanInAppHint(
        isStandaloneDisplayMode() && isAppleMobile(navigator),
      );
      setIsReady(true);
    });

    return () => {
      active = false;
    };
  }, [installationTrackingReference, router]);

  return (
    <section className="flex flex-1 flex-col">
      <CustomerToolbar className="self-end" />
      <div className="flex flex-1 items-center justify-center py-6">
        {isReady ? (
          <Button
            aria-label="Zeskanuj kod QR"
            className="empty-scan-action"
            variant="tertiary"
            onPress={() => router.push("/scan")}
          >
            <span className="empty-scan-action-label">Zeskanuj kod QR</span>
            <QrCodeIcon className="empty-scan-action-icon" size={128} />
          </Button>
        ) : (
          <Spinner aria-label="Otwieranie Kairos" />
        )}
      </div>
      {isReady && showScanInAppHint && (
        <p className="mx-auto w-full max-w-xs text-center text-sm text-muted">
          Kolejne zamówienia skanuj w tej aplikacji.
        </p>
      )}
      <Link
        className="min-h-11 self-center text-sm text-muted"
        href="/prywatnosc"
      >
        Prywatność
      </Link>
    </section>
  );
}

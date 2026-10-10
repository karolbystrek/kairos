"use client";

import type QrScanner from "qr-scanner";

import { Alert, Button, Spinner, Tooltip } from "@heroui/react";
import { RefreshCw, SwitchCamera, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { disposeCameraSession } from "@/src/scanner/camera-session";
import {
  resolveScanResult,
  type ScanResultResolution,
} from "@/src/scanner/scanner-state";

type ScannerView =
  | "camera-unavailable"
  | "requesting-camera"
  | "scanning"
  | "waiting-for-connectivity";

type PendingOrder = Extract<
  ScanResultResolution,
  { kind: "wait-for-connectivity" }
>;

export function OrderScanner() {
  const router = useRouter();
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const scannerRef = useRef<QrScanner | null>(null);
  const hasHandledResult = useRef(false);
  const [cameras, setCameras] = useState<QrScanner.Camera[]>([]);
  const [currentCameraId, setCurrentCameraId] = useState<string | null>(null);
  const [inlineError, setInlineError] = useState<string | null>(null);
  const [isOnline, setIsOnline] = useState(true);
  const [isSwitchingCamera, setIsSwitchingCamera] = useState(false);
  const [pendingOrder, setPendingOrder] = useState<PendingOrder | null>(null);
  const [view, setView] = useState<ScannerView>("requesting-camera");

  const stopCamera = useCallback(() => {
    disposeCameraSession(scannerRef.current, videoRef.current);
    scannerRef.current = null;
  }, []);

  const navigateToOrder = useCallback(
    (href: string) => {
      hasHandledResult.current = true;
      stopCamera();
      router.replace(href);
    },
    [router, stopCamera],
  );

  const handleDecodedValue = useCallback(
    (value: string) => {
      if (hasHandledResult.current) {
        return;
      }
      const resolution = resolveScanResult(value, navigator.onLine);

      if (resolution.kind === "invalid") {
        setInlineError(
          "Ten kod QR nie prowadzi do zamówienia Kairos. Skanuj dalej.",
        );

        return;
      }
      setInlineError(null);
      if (resolution.kind === "wait-for-connectivity") {
        hasHandledResult.current = true;
        stopCamera();
        setPendingOrder(resolution);
        setView("waiting-for-connectivity");

        return;
      }
      navigateToOrder(resolution.href);
    },
    [navigateToOrder, stopCamera],
  );

  useEffect(() => {
    const synchronizeConnectivity = () => {
      setIsOnline(navigator.onLine);
    };

    synchronizeConnectivity();
    window.addEventListener("online", synchronizeConnectivity);
    window.addEventListener("offline", synchronizeConnectivity);

    return () => {
      window.removeEventListener("online", synchronizeConnectivity);
      window.removeEventListener("offline", synchronizeConnectivity);
    };
  }, []);

  useEffect(() => {
    let active = true;

    void (async () => {
      if (!navigator.mediaDevices?.getUserMedia || !videoRef.current) {
        setView("camera-unavailable");

        return;
      }
      try {
        const { default: QrScannerConstructor } = await import("qr-scanner");

        if (!active || !videoRef.current) {
          return;
        }
        const scanner = new QrScannerConstructor(
          videoRef.current,
          (result) => handleDecodedValue(result.data),
          {
            highlightCodeOutline: true,
            highlightScanRegion: true,
            maxScansPerSecond: 12,
            preferredCamera: "environment",
            returnDetailedScanResult: true,
          },
        );

        scannerRef.current = scanner;
        await scanner.start();
        if (
          !active ||
          hasHandledResult.current ||
          scannerRef.current !== scanner
        ) {
          disposeCameraSession(scanner, videoRef.current);

          return;
        }
        setView("scanning");
        const availableCameras = await QrScannerConstructor.listCameras();

        if (!active || scannerRef.current !== scanner) {
          return;
        }
        setCameras(availableCameras);
        const preferredCamera =
          availableCameras.find(({ label }) =>
            /back|rear|environment/i.test(label),
          ) ?? availableCameras[0];

        setCurrentCameraId(preferredCamera?.id ?? null);
      } catch {
        if (active && !hasHandledResult.current) {
          stopCamera();
          setView("camera-unavailable");
        }
      }
    })();

    return () => {
      active = false;
      stopCamera();
    };
  }, [handleDecodedValue, stopCamera]);

  const cancel = () => {
    hasHandledResult.current = true;
    stopCamera();
    router.replace("/");
  };

  const switchCamera = async () => {
    const scanner = scannerRef.current;

    if (!scanner || cameras.length < 2 || isSwitchingCamera) {
      return;
    }
    const currentIndex = cameras.findIndex(({ id }) => id === currentCameraId);
    const nextCamera = cameras[(currentIndex + 1) % cameras.length];

    if (!nextCamera) {
      return;
    }
    setIsSwitchingCamera(true);
    setInlineError(null);
    try {
      await scanner.setCamera(nextCamera.id);
      setCurrentCameraId(nextCamera.id);
    } catch {
      setInlineError("Nie udało się przełączyć aparatu. Skanuj dalej.");
    } finally {
      setIsSwitchingCamera(false);
    }
  };

  if (view === "camera-unavailable") {
    return (
      <section className="flex min-h-[60vh] flex-col justify-center gap-6">
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Aparat niedostępny</Alert.Title>
            <Alert.Description>
              Otwórz aplikację aparatu na urządzeniu i zeskanuj w niej kod QR
              restauracji.
            </Alert.Description>
          </Alert.Content>
        </Alert>
        <Button variant="secondary" onPress={cancel}>
          <X aria-hidden="true" size={18} /> Wróć do skanera
        </Button>
      </section>
    );
  }

  if (view === "waiting-for-connectivity" && pendingOrder) {
    return (
      <section className="flex min-h-[60vh] flex-col justify-center gap-6">
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Znaleziono zamówienie</Alert.Title>
            <Alert.Description>
              Połącz się z internetem i spróbuj ponownie wczytać zamówienie.
            </Alert.Description>
          </Alert.Content>
        </Alert>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button
            isDisabled={!isOnline}
            variant="primary"
            onPress={() => navigateToOrder(pendingOrder.href)}
          >
            <RefreshCw aria-hidden="true" size={18} />
            {isOnline ? "Spróbuj ponownie" : "Oczekiwanie na połączenie…"}
          </Button>
          <Button aria-label="Anuluj" variant="secondary" onPress={cancel}>
            Anuluj
          </Button>
        </div>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="page-title">Zeskanuj zamówienie</h1>
          <p className="mt-3 secondary-text">
            Skieruj aparat na kod QR Kairos.
          </p>
        </div>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label="Zamknij skaner"
              className="rounded-md"
              variant="tertiary"
              onPress={cancel}
            >
              <X aria-hidden="true" size={20} />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content>Zamknij skaner</Tooltip.Content>
        </Tooltip>
      </div>

      <div className="relative aspect-[3/4] max-h-[68vh] min-h-80 overflow-hidden rounded-[var(--radius-large)] bg-black">
        <video
          ref={videoRef}
          muted
          playsInline
          aria-label="Podgląd aparatu do skanowania kodu QR zamówienia"
          className="size-full object-cover"
        />
        {view === "requesting-camera" && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/70 text-white">
            <Spinner aria-label="Oczekiwanie na dostęp do aparatu" />
            <p className="text-sm">Oczekiwanie na dostęp do aparatu…</p>
          </div>
        )}
        {view === "scanning" && (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-[14%] rounded-[var(--radius-medium)] border-2 border-white/90 shadow-[0_0_0_999px_rgba(0,0,0,0.22)]"
          />
        )}
      </div>

      <div className="flex min-h-11 items-center justify-between gap-4">
        <p aria-live="polite" className="text-sm text-danger">
          {inlineError}
        </p>
        {cameras.length > 1 && (
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                aria-label="Przełącz aparat"
                className="rounded-md"
                isDisabled={isSwitchingCamera}
                size="sm"
                variant="secondary"
                onPress={() => {
                  void switchCamera();
                }}
              >
                <SwitchCamera aria-hidden="true" size={20} />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>Przełącz aparat</Tooltip.Content>
          </Tooltip>
        )}
      </div>
    </section>
  );
}

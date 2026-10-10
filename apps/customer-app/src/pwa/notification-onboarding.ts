import type { NotificationState } from "@/src/pwa/notification-provider";

const DISMISSAL_KEY = "kairos.notification-guide-dismissed.v1";

export function isAppleMobile(
  device: Pick<Navigator, "userAgent" | "maxTouchPoints">,
): boolean {
  return (
    /iPad|iPhone|iPod/.test(device.userAgent) ||
    (/Macintosh/.test(device.userAgent) && device.maxTouchPoints > 1)
  );
}

export function isStandalone(): boolean {
  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

export function requiresNotificationInstallation(): boolean {
  return (
    typeof window !== "undefined" && isAppleMobile(navigator) && !isStandalone()
  );
}

export function shouldShowNotificationGuide({
  state,
  installationRequired,
  dismissed,
  automatic,
}: {
  state: NotificationState;
  installationRequired: boolean;
  dismissed: boolean;
  automatic: boolean;
}): boolean {
  return (
    installationRequired &&
    state !== "loading" &&
    state !== "enabled" &&
    !(automatic && dismissed)
  );
}

export function readGuideDismissal(): boolean {
  try {
    return window.localStorage.getItem(DISMISSAL_KEY) === "1";
  } catch {
    return false;
  }
}

export function rememberGuideDismissal(): void {
  try {
    window.localStorage.setItem(DISMISSAL_KEY, "1");
  } catch {
    // The provider remembers dismissal for this session when storage is unavailable.
  }
}

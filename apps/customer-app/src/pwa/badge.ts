import { countActiveTrackedOrders } from "@/src/pwa/storage";

type BadgeNavigator = Navigator & {
  clearAppBadge?: () => Promise<void>;
  setAppBadge?: (contents?: number) => Promise<void>;
};

type BadgeRegistration = ServiceWorkerRegistration & {
  clearAppBadge?: () => Promise<void>;
  setAppBadge?: (contents?: number) => Promise<void>;
};

export async function updateApplicationBadge(
  activeOrderCount?: number,
): Promise<void> {
  const count = activeOrderCount ?? (await countActiveTrackedOrders());
  const target =
    typeof navigator !== "undefined"
      ? (navigator as BadgeNavigator)
      : ((globalThis as { registration?: BadgeRegistration }).registration ??
        null);

  try {
    if (count === 0) {
      await target?.clearAppBadge?.();
    } else {
      await target?.setAppBadge?.(count);
    }
  } catch {
    // Badging is a best-effort progressive enhancement.
  }
}

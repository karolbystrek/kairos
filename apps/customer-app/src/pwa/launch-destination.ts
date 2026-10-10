import type { StableCustomerDestination } from "@/src/pwa/storage";

import { getTrackedOrderHref } from "@/src/orders/order-route";

export function resolveCustomerLaunchHref({
  installationTrackingReference,
  lastDestination,
}: {
  installationTrackingReference: string | null;
  lastDestination: StableCustomerDestination | null;
}): string {
  if (installationTrackingReference) {
    return getTrackedOrderHref(installationTrackingReference);
  }

  if (lastDestination?.kind !== "order") {
    return "/";
  }

  return getTrackedOrderHref(lastDestination.trackingReference);
}

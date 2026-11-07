import type { StableCustomerDestination } from "@/src/pwa/storage";

import { orderTrackingPath, paths } from "@/src/routes";

export function resolveCustomerLaunchHref({
  installationTrackingReference,
  lastDestination,
}: {
  installationTrackingReference: string | null;
  lastDestination: StableCustomerDestination | null;
}): string {
  if (installationTrackingReference) {
    return orderTrackingPath(installationTrackingReference);
  }

  if (lastDestination?.kind !== "order") {
    return paths.customerHome;
  }

  return orderTrackingPath(lastDestination.trackingReference);
}

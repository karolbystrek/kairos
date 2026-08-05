import type {
  StableCustomerDestination,
  StoredTrackedOrder,
} from "@/src/pwa/storage";

import { isActiveOrderStatus } from "@/src/orders/order-status";
import { getTrackedOrderHref } from "@/src/orders/order-route";

export function resolveInstalledLaunchHref({
  installationTrackingReference,
  lastDestination,
  orders,
}: {
  installationTrackingReference: string | null;
  lastDestination: StableCustomerDestination | null;
  orders: StoredTrackedOrder[];
}): string {
  if (installationTrackingReference) {
    return getTrackedOrderHref(installationTrackingReference);
  }

  if (lastDestination?.kind !== "order") {
    return "/";
  }
  const order = orders.find(
    ({ trackingReference }) =>
      trackingReference === lastDestination.trackingReference,
  );

  return order && isActiveOrderStatus(order.status)
    ? getTrackedOrderHref(order.trackingReference)
    : "/";
}

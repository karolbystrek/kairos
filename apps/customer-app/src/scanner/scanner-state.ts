import {
  getTrackedOrderHref,
  parseScannedOrderUrl,
} from "@/src/orders/order-route";

export type ScanResultResolution =
  | { kind: "invalid" }
  | {
      href: string;
      kind: "navigate";
      trackingReference: string;
    }
  | {
      href: string;
      kind: "wait-for-connectivity";
      trackingReference: string;
    };

export function resolveScanResult(
  value: string,
  online: boolean,
  configuredCustomerUrl = process.env.NEXT_PUBLIC_CUSTOMER_APP_URL,
): ScanResultResolution {
  const trackingReference = parseScannedOrderUrl(value, configuredCustomerUrl);

  if (!trackingReference) {
    return { kind: "invalid" };
  }

  return {
    kind: online ? "navigate" : "wait-for-connectivity",
    trackingReference,
    href: getTrackedOrderHref(trackingReference),
  };
}

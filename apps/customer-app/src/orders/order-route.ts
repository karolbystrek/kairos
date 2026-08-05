const TRACKING_REFERENCE_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function getTrackedOrderHref(trackingReference: string): string {
  return `/orders/${encodeURIComponent(trackingReference)}`;
}

export function parseScannedOrderUrl(
  value: string,
  configuredCustomerUrl = process.env.NEXT_PUBLIC_CUSTOMER_APP_URL,
): string | null {
  if (!configuredCustomerUrl) {
    return null;
  }

  try {
    const configuredUrl = new URL(configuredCustomerUrl);
    const candidate = new URL(value);
    const match = candidate.pathname.match(/^\/orders\/([^/]+)$/);

    if (
      candidate.origin !== configuredUrl.origin ||
      candidate.username ||
      candidate.password ||
      candidate.search ||
      candidate.hash ||
      !match?.[1] ||
      !TRACKING_REFERENCE_PATTERN.test(match[1])
    ) {
      return null;
    }

    return match[1];
  } catch {
    return null;
  }
}

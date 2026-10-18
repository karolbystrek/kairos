const TRACKING_REFERENCE_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isTrackingReference(value: string): boolean {
  return TRACKING_REFERENCE_PATTERN.test(value);
}

export function getTrackedOrderHref(trackingReference: string): string {
  return `/orders/${encodeURIComponent(trackingReference)}`;
}

export function parseScannedOrderUrl(
  value: string,
  customerOrigin = globalThis.location?.origin,
): string | null {
  if (!customerOrigin) {
    return null;
  }

  try {
    const candidate = new URL(value);
    const match = candidate.pathname.match(/^\/orders\/([^/]+)$/);

    if (
      candidate.origin !== customerOrigin ||
      candidate.username ||
      candidate.password ||
      candidate.search ||
      candidate.hash ||
      !match?.[1] ||
      !isTrackingReference(match[1])
    ) {
      return null;
    }

    return match[1];
  } catch {
    return null;
  }
}

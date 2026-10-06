import { describe, expect, it } from "vitest";

import { parseScannedOrderUrl } from "@/src/orders/order-route";

const CUSTOMER_URL = "https://customer.kairos.test";
const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";

describe("scanned order routes", () => {
  it("accepts an exact Kairos order URL", () => {
    expect(
      parseScannedOrderUrl(
        `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}`,
        CUSTOMER_URL,
      ),
    ).toBe(TRACKING_REFERENCE);
  });

  it.each([
    `https://other.test/orders/${TRACKING_REFERENCE}`,
    `${CUSTOMER_URL}/orders/not-a-uuid`,
    `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}/`,
    `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}?source=qr`,
    `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}#status`,
    `/orders/${TRACKING_REFERENCE}`,
  ])("rejects non-canonical scanned values: %s", (value) => {
    expect(parseScannedOrderUrl(value, CUSTOMER_URL)).toBeNull();
  });
});

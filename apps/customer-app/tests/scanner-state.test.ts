import { describe, expect, it } from "vitest";

import { resolveScanResult } from "@/src/scanner/scanner-state";

const CUSTOMER_URL = "https://customer.kairos.test";
const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
const ORDER_URL = `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}`;

describe("scanner result state", () => {
  it("navigates immediately for a valid online result", () => {
    expect(resolveScanResult(ORDER_URL, true, CUSTOMER_URL)).toEqual({
      href: `/orders/${TRACKING_REFERENCE}`,
      kind: "navigate",
      trackingReference: TRACKING_REFERENCE,
    });
  });

  it("retains a valid offline result for retry", () => {
    expect(resolveScanResult(ORDER_URL, false, CUSTOMER_URL)).toEqual({
      href: `/orders/${TRACKING_REFERENCE}`,
      kind: "wait-for-connectivity",
      trackingReference: TRACKING_REFERENCE,
    });
  });

  it("keeps scanning after an invalid result", () => {
    expect(resolveScanResult("not a Kairos URL", true, CUSTOMER_URL)).toEqual({
      kind: "invalid",
    });
  });
});

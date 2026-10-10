import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { SWRConfig, unstable_serialize } from "swr";
import { afterAll, describe, expect, it, vi } from "vitest";

import { OrderTracker } from "@/app/orders/[trackingReference]/order-tracker";
import type { OrderStatus } from "@/src/orders/order-status";
import { CustomerPwaProvider } from "@/src/pwa/notification-provider";

vi.hoisted(() => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.example");
});

afterAll(() => vi.unstubAllEnvs());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";

function renderOrder(status?: OrderStatus) {
  const key = unstable_serialize(["tracked-order", TRACKING_REFERENCE]);

  return renderToStaticMarkup(
    createElement(
      SWRConfig,
      {
        value: {
          provider: () => new Map(),
          fallback: status
            ? {
                [key]: {
                  label: "A12",
                  status,
                  updatedAt: "2026-10-09T20:00:00Z",
                },
              }
            : {},
        },
      },
      createElement(
        CustomerPwaProvider,
        null,
        createElement(OrderTracker, { trackingReference: TRACKING_REFERENCE }),
      ),
    ),
  );
}

describe("order exit controls", () => {
  it.each([undefined, "IN_PREPARATION", "READY"] as const)(
    "provides no close action while the order is %s",
    (status) => {
      expect(renderOrder(status)).not.toContain("Zamknij zamówienie");
    },
  );

  it.each(["COMPLETED", "CANCELED"] as const)(
    "offers closing after the order is %s",
    (status) => {
      expect(renderOrder(status)).toMatch(/<button[^>]*>.*Zamknij zamówienie<\/button>/);
    },
  );
});

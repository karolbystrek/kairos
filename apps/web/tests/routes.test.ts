import { describe, expect, it } from "vitest";

import { createKairosManifest } from "@/src/pwa/manifest";
import {
  installationStartPath,
  orderManifestPath,
  orderTrackingPath,
  panelPath,
  parseOrderTrackingPath,
  parsePanelPath,
  parseScannedOrderUrl,
  paths,
  type PanelRoute,
} from "@/src/routes";

const CUSTOMER_URL = "https://kairos.test";
const TRACKING_REFERENCE = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
const LOCATION = "00000000-0000-4000-8000-000000000001";
const ORDER = "00000000-0000-4000-8000-000000000002";

describe("customer routes", () => {
  it("builds order tracking, manifest and installation paths under /orders", () => {
    expect(orderTrackingPath(TRACKING_REFERENCE)).toBe(`/orders/${TRACKING_REFERENCE}`);
    expect(orderManifestPath(TRACKING_REFERENCE)).toBe(
      `/orders/${TRACKING_REFERENCE}/manifest.webmanifest`,
    );
    expect(installationStartPath(TRACKING_REFERENCE)).toBe(
      `/orders?install-order=${TRACKING_REFERENCE}`,
    );
  });

  it("recognizes only tracking references as order paths", () => {
    expect(parseOrderTrackingPath(`/orders/${TRACKING_REFERENCE}`)).toBe(TRACKING_REFERENCE);
    expect(parseOrderTrackingPath("/orders/scan")).toBeNull();
    expect(parseOrderTrackingPath("/orders/~offline")).toBeNull();
    expect(parseOrderTrackingPath(`/o/${TRACKING_REFERENCE}`)).toBeNull();
  });

  it("keeps the installed app inside the /orders scope", () => {
    const manifest = createKairosManifest(installationStartPath(TRACKING_REFERENCE));

    expect(manifest.id).toBe("/orders");
    expect(manifest.scope).toBe("/orders");
    expect(manifest.start_url?.startsWith(manifest.scope!)).toBe(true);
    expect(paths.customerHome.startsWith(paths.serviceWorkerScope)).toBe(true);
    expect(paths.serviceWorker.startsWith(paths.serviceWorkerScope)).toBe(true);
  });
});

describe("scanned order URLs", () => {
  it("accepts an exact Kairos order URL", () => {
    expect(
      parseScannedOrderUrl(`${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}`, CUSTOMER_URL),
    ).toBe(TRACKING_REFERENCE);
  });

  it.each([
    `${CUSTOMER_URL}/o/${TRACKING_REFERENCE}`,
    `https://other.test/orders/${TRACKING_REFERENCE}`,
    `${CUSTOMER_URL}/orders/not-a-uuid`,
    `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}/`,
    `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}?source=qr`,
    `${CUSTOMER_URL}/orders/${TRACKING_REFERENCE}#status`,
    `${CUSTOMER_URL}/panel/orders/${TRACKING_REFERENCE}`,
    `/orders/${TRACKING_REFERENCE}`,
  ])("rejects non-canonical scanned values: %s", (value) => {
    expect(parseScannedOrderUrl(value, CUSTOMER_URL)).toBeNull();
  });
});

describe("panel workspace routes", () => {
  it.each<[PanelRoute, string]>([
    [{ workspace: "orders" }, "/panel/orders"],
    [{ workspace: "orders", orderId: ORDER }, `/panel/orders/${ORDER}`],
    [{ workspace: "orders", locationId: LOCATION }, `/panel/locations/${LOCATION}/orders`],
    [
      { workspace: "orders", locationId: LOCATION, orderId: ORDER },
      `/panel/locations/${LOCATION}/orders/${ORDER}`,
    ],
    [{ workspace: "menus" }, "/panel/menu"],
    [{ workspace: "menus", locationId: LOCATION }, `/panel/locations/${LOCATION}/menu`],
    [{ workspace: "locations" }, "/panel/locations"],
    [{ workspace: "accounts" }, "/panel/staff"],
    [{ workspace: "integrations" }, "/panel/integrations"],
    [{ workspace: "analytics" }, "/panel/analytics"],
    [{ workspace: "platform" }, "/panel/platform"],
  ])("maps %j to %s and back", (route, path) => {
    expect(panelPath(route)).toBe(path);
    expect(parsePanelPath(path)).toEqual(route);
  });

  it.each([
    "/panel",
    "/panel/login",
    "/panel/unknown",
    `/panel/locations/${LOCATION}`,
    `/panel/locations/${LOCATION}/staff`,
    `/panel/locations/${LOCATION}/menu/${ORDER}`,
    `/panel/staff/${ORDER}`,
    `/panel/orders/${ORDER}/extra`,
    "/orders",
  ])("rejects %s", (path) => {
    expect(parsePanelPath(path)).toBeNull();
  });
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
function stream(locationId) {
  let source;
  const snapshots = [];
  let failures = 0;
  class EventSource {
    constructor(url, options) { source = this; this.url = url; this.options = options; }
    addEventListener(name, listener) { assert.equal(name, "order-queue"); this.listener = listener; }
    close() { this.closed = true; }
  }
  const exports = {};
  const code = ts.transpileModule(readFileSync(new URL("../src/api/orders.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
  new Function("require", "exports", "EventSource", code)(name => {
    if (name === "./api-fetch") return {};
    if (name === "@/src/config/public-environment") return { apiBaseUrl: "https://api.example.test" };
    return require(name);
  }, exports, EventSource);
  const close = exports.subscribeToOrderQueue(locationId, value => snapshots.push(value), () => failures++);
  return { source, snapshots, close, failures: () => failures };
}

const order = {
  id: "00000000-0000-4000-8000-000000000001",
  locationId: "00000000-0000-4000-8000-000000000002",
  trackingReference: "00000000-0000-4000-8000-000000000003",
  number: 1,
  label: null,
  items: [{ name: "Burger", quantity: 2 }],
  status: "READY",
  createdAt: "2026-10-12T10:00:00Z",
  updatedAt: "2026-10-12T10:01:00Z",
};

test("credentialed queue stream accepts validated full snapshots including removals and closes on cleanup", () => {
  const view = stream(order.locationId);
  assert.equal(view.source.url.href, `https://api.example.test/api/orders/v1/events?locationId=${order.locationId}`);
  assert.deepEqual(view.source.options, { withCredentials: true });
  view.source.listener({ data: JSON.stringify([order]) });
  view.source.listener({ data: "[]" });
  assert.deepEqual(view.snapshots, [[order], []]);
  view.close();
  assert.equal(view.source.closed, true);
});

test("tenant stream omits location and invalid snapshots or disconnects request REST recovery", () => {
  const view = stream();
  assert.equal(view.source.url.search, "");
  view.source.listener({ data: "not json" });
  view.source.listener({ data: JSON.stringify([{ ...order, status: "UNKNOWN" }]) });
  view.source.onerror();
  assert.equal(view.failures(), 3);
  assert.deepEqual(view.snapshots, []);
  view.source.listener({ data: JSON.stringify([order]) });
  assert.deepEqual(view.snapshots, [[order]]);
});

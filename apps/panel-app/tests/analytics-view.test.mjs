import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
const report = {
  from: "2026-10-11", to: "2026-10-13", zone: "Europe/Warsaw",
  generatedAt: "2026-10-13T12:00:00Z", firstRecordedHour: "2026-10-11T23:00:00Z",
  partialDay: "2026-10-13", transitions: { COMPLETED: 8 },
  preparation: { meanMs: 60000 }, collection: { meanMs: null },
  days: [
    { date: "2026-10-11", transitions: {} },
    { date: "2026-10-12", transitions: {} },
    { date: "2026-10-13", transitions: { IN_PREPARATION: 4, COMPLETED: 2 } },
  ],
  actions: { queue_opened: 19, review_link_clicked: 3 }, locations: null,
};

function view(role = "ADMIN", response = report) {
  const exports = {};
  const ui = new Proxy({}, { get: (_, key) => new Proxy(() => {}, { get: (_, part) => `${String(key)}.${String(part)}` }) });
  const source = ts.transpileModule(
    readFileSync(new URL("../components/analytics-management.tsx", import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } },
  ).outputText;
  new Function("require", "exports", source)(name => {
    if (name === "react") return { useState: initial => [typeof initial === "function" ? initial() : initial, () => {}] };
    if (name === "swr") return {
      default: key => ({ data: key === "locations" ? [] : key === "health" ? undefined : response }),
      useSWRConfig: () => ({ mutate() {} }),
    };
    if (name === "@/src/api/api-fetch") return { ApiError: class ApiError extends Error {} };
    if (name === "@heroui/react") return ui;
    if (name === "recharts" || name === "@/components/chart" || name === "@phosphor-icons/react") return new Proxy({}, { get: (_, key) => String(key) });
    if (name === "@/src/api/cache-keys") return {
      staffLocationsKey: () => "locations", staffAnalyticsKey: () => "report", staffPlatformHealthKey: () => "health",
    };
    if (name === "@/src/api/report-range") {
      const values = {};
      new Function("exports", ts.transpileModule(readFileSync(new URL("../src/api/report-range.ts", import.meta.url), "utf8"),
        { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText)(values);
      return values;
    }
    if (name === "@/src/api/locations") return { sortLocations: values => values };
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  }, exports);
  function expand(node) {
    if (Array.isArray(node)) return node.flatMap(expand);
    if (!node || typeof node !== "object") return [node];
    if (typeof node.type === "function" && node.type.name) return expand(node.type(node.props));
    return [node, ...expand(node.props?.children)];
  }
  const tree = expand(role === "PLATFORM"
    ? exports.PlatformAnalytics({ accountId: "staff", onAccessLost() {} })
    : exports.RestaurantAnalytics({ account: { accountId: "staff", tenantRole: role, assignment: { locationId: "location" } }, onAccessLost() {} }));
  return {
    tree, expand,
    text: tree.filter(node => typeof node === "string" || typeof node === "number").join(" "),
    charts: tree.filter(node => node?.type === "BarChart"),
    tooltips: tree.filter(node => node?.type === "ChartTooltip"),
  };
}

test("restaurant audiences see review metrics while panel usage and health remain platform-only", () => {
  for (const role of ["ADMIN", "MEMBER"]) {
    const page = view(role);
    assert.match(page.text, /Kliknięcia linku do opinii Google/);
    assert.doesNotMatch(page.text, /Korzystanie z panelu|Stan systemu|Otwarcia kolejki/);
  }
  assert.match(view("PLATFORM").text, /Korzystanie z panelu/);
  assert.match(view("PLATFORM").text, /Stan systemu/);
});

test("pre-collection days are empty, recorded zero days remain zero, and today retains its details", () => {
  const page = view();
  const days = page.charts[0].props.data;
  assert.deepEqual(days.map(day => day.value), [null, 0, 4]);
  assert.equal(days[2].partial, true);
  const content = page.tooltips[0].props.content;
  assert.equal(content({ active: true, payload: [{ payload: days[0] }] }), null);
  const details = page.expand(content({ active: true, payload: [{ payload: days[2] }] })).filter(node => typeof node === "string").join(" ");
  assert.match(details, /Dzień w toku/);
  assert.match(details, /Zakończone 2/);
  assert.deepEqual(view("ADMIN", { ...report, firstRecordedHour: null }).charts[0].props.data.map(day => day.value), [null, null, null]);
});

test("location comparison uses completed orders and keeps full long names in details", () => {
  const name = "Lokal o bardzo długiej nazwie — Kraków";
  const page = view("ADMIN", { ...report, locations: [{ locationId: "location", name, transitions: { IN_PREPARATION: 20, COMPLETED: 8 } }] });
  assert.equal(page.charts[1].props.data[0].value, 8);
  const details = page.expand(page.tooltips[1].props.content({ active: true, payload: [{ payload: page.charts[1].props.data[0] }] })).filter(node => typeof node === "string").join(" ");
  assert.match(details, new RegExp(name));
});

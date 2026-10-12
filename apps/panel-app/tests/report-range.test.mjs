import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import ts from "typescript";

const exports = {};
new Function("exports", ts.transpileModule(
  readFileSync(new URL("../src/api/report-range.ts", import.meta.url), "utf8"),
  { compilerOptions: { module: ts.ModuleKind.CommonJS } },
).outputText)(exports);
const { localDate, reportRange } = exports;

test("presets end today and include it", () => {
  assert.deepEqual(reportRange("7", {}, "2026-03-02"), { from: "2026-02-24", to: "2026-03-02" });
  assert.deepEqual(reportRange("30", {}, "2026-03-02"), { from: "2026-02-01", to: "2026-03-02" });
});

test("custom ranges are complete, ordered, past-or-today and at most 90 calendar days", () => {
  const today = "2026-11-01";
  assert.deepEqual(reportRange("custom", { from: "2026-08-04", to: today }, today), { from: "2026-08-04", to: today });
  for (const custom of [{ from: "2026-08-03", to: today }, { from: today, to: "2026-11-02" },
    { from: today, to: "2026-10-31" }, { from: today }]) {
    assert.ok("error" in reportRange("custom", custom, today), JSON.stringify(custom));
  }
});

test("the browser-local reporting date follows the explicit zone", () => {
  const instant = new Date("2026-10-31T23:30:00Z");
  assert.equal(localDate(instant, "UTC"), "2026-10-31");
  assert.equal(localDate(instant, "Europe/Warsaw"), "2026-11-01");
  assert.equal(localDate(new Date("2026-10-29T18:30:00Z"), "Asia/Kolkata"), "2026-10-30");
});

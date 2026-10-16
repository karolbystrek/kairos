import { expect, it } from "vitest";

import { localDate, reportRange } from "@/src/api/report-range";

it("presets end today and include it", () => {
  expect(reportRange("7", {}, "2026-03-02")).toEqual({ from: "2026-02-24", to: "2026-03-02" });
  expect(reportRange("30", {}, "2026-03-02")).toEqual({ from: "2026-02-01", to: "2026-03-02" });
});

it("custom ranges are complete, ordered, past-or-today and at most 90 calendar days", () => {
  const today = "2026-11-01";

  expect(reportRange("custom", { from: "2026-08-04", to: today }, today)).toEqual({ from: "2026-08-04", to: today });
  for (const custom of [{ from: "2026-08-03", to: today }, { from: today, to: "2026-11-02" },
    { from: today, to: "2026-10-31" }, { from: today }]) {
    expect(reportRange("custom", custom, today), JSON.stringify(custom)).toHaveProperty("error");
  }
});

it("the browser-local reporting date follows the explicit zone", () => {
  const instant = new Date("2026-10-31T23:30:00Z");

  expect(localDate(instant, "UTC")).toBe("2026-10-31");
  expect(localDate(instant, "Europe/Warsaw")).toBe("2026-11-01");
  expect(localDate(new Date("2026-10-29T18:30:00Z"), "Asia/Kolkata")).toBe("2026-10-30");
});

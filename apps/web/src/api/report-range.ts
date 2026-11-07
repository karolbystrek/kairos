const maximumReportDays = 90;

export type RangePreset = "7" | "30" | "custom";
export type ReportRange = { from: string; to: string };

export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/** Calendar date (YYYY-MM-DD) of an instant in an IANA zone. */
export function localDate(instant: Date, zone: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(instant);
}

function addDays(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`);

  value.setUTCDate(value.getUTCDate() + days);

  return value.toISOString().slice(0, 10);
}

/** Presets end today; custom ranges are past-or-today, ordered and at most 90 calendar days. */
export function reportRange(
  preset: RangePreset,
  custom: Partial<ReportRange>,
  today: string,
): ReportRange | { error: string } {
  if (preset !== "custom") {
    return { from: addDays(today, 1 - Number(preset)), to: today };
  }
  const { from, to } = custom;

  if (!from || !to) return { error: "Podaj datę początkową i końcową." };
  if (from > to)
    return { error: "Data początkowa nie może być późniejsza niż końcowa." };
  if (to > today) return { error: "Zakres nie może obejmować przyszłych dni." };
  if (addDays(from, maximumReportDays - 1) < to)
    return {
      error: `Zakres może obejmować najwyżej ${maximumReportDays} dni.`,
    };

  return { from, to };
}

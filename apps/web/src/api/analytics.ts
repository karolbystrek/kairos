import { z } from "zod";

import { request } from "./api-fetch";

const countsSchema = z.record(z.string(), z.number().int().nonnegative());
const meanSchema = z.object({
  samples: z.number().int().nonnegative(),
  meanMs: z.number().nonnegative().nullable(),
});

const analyticsReportSchema = z.object({
  from: z.iso.date(),
  to: z.iso.date(),
  zone: z.string(),
  generatedAt: z.iso.datetime({ offset: true }),
  partialDay: z.iso.date().nullable(),
  firstRecordedHour: z.iso.datetime({ offset: true }).nullable(),
  transitions: countsSchema,
  preparation: meanSchema,
  collection: meanSchema,
  days: z.array(z.object({ date: z.iso.date(), transitions: countsSchema })),
  actions: countsSchema,
  locations: z
    .array(
      z.object({
        locationId: z.uuid(),
        name: z.string().nullable(),
        transitions: countsSchema,
      }),
    )
    .nullable(),
});

const statusSchema = z.enum(["UP", "DOWN", "UNKNOWN"]);

const platformHealthSchema = z.object({
  observedAt: z.iso.datetime({ offset: true }),
  database: statusSchema,
  redis: statusSchema,
  backlogObservedAt: z.iso.datetime({ offset: true }).nullable(),
  backlogCurrent: z.boolean(),
  workers: z.array(
    z.object({
      worker: z.string(),
      pending: z.number().int().nonnegative(),
      oldestAgeSeconds: z.number().nonnegative(),
    }),
  ),
});

export type AnalyticsReport = z.infer<typeof analyticsReportSchema>;
export type PlatformHealth = z.infer<typeof platformHealthSchema>;
type ReportQuery = {
  from: string;
  to: string;
  zone: string;
  locationId?: string;
};

function query({ from, to, zone, locationId }: ReportQuery): string {
  return new URLSearchParams({
    from,
    to,
    zone,
    ...(locationId ? { locationId } : {}),
  }).toString();
}

export function getRestaurantReport(
  parameters: ReportQuery,
): Promise<AnalyticsReport> {
  return request(
    `/api/analytics/v1/report?${query(parameters)}`,
    analyticsReportSchema,
  );
}

export function getPlatformReport(
  parameters: ReportQuery,
): Promise<AnalyticsReport> {
  return request(
    `/api/platform-analytics/v1/report?${query(parameters)}`,
    analyticsReportSchema,
  );
}

export function getPlatformHealth(): Promise<PlatformHealth> {
  return request("/api/platform-analytics/v1/health", platformHealthSchema);
}

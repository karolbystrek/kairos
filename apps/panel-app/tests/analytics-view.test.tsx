import { act, fireEvent, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";

import { fakeApi, renderWithSWR, staffAccount } from "./helpers";

import { PlatformAnalytics, RestaurantAnalytics } from "@/components/analytics-management";
import type { CurrentAccount } from "@/src/api/authentication";

const locationId = "00000000-0000-4000-8000-000000000001";
const report = {
  from: "2026-10-11", to: "2026-10-13", zone: "Europe/Warsaw",
  generatedAt: "2026-10-13T12:00:00Z", firstRecordedHour: "2026-10-11T23:00:00Z" as string | null,
  partialDay: "2026-10-13", transitions: { COMPLETED: 8 },
  preparation: { samples: 1, meanMs: 60000 }, collection: { samples: 0, meanMs: null },
  days: [
    { date: "2026-10-11", transitions: {} },
    { date: "2026-10-12", transitions: {} },
    { date: "2026-10-13", transitions: { IN_PREPARATION: 4, COMPLETED: 2 } },
  ],
  actions: { queue_opened: 19, review_link_clicked: 3 },
  locations: null as null | { locationId: string; name: string; transitions: Record<string, number> }[],
};

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
});

async function restaurant(tenantRole = "ADMIN", response = report) {
  fakeApi({
    "GET /api/locations/v1": () => [],
    "GET /api/analytics/v1/report": () => response,
  });
  const account = staffAccount([], { tenantRole, assignment: tenantRole === "MEMBER" ? { locationId, role: "OPERATOR" } : null });

  renderWithSWR(<RestaurantAnalytics account={account as CurrentAccount} onAccessLost={() => {}} />);
  await screen.findByText("Kliknięcia linku do opinii Google");
}

/** Walks a chart with its keyboard accessibility layer and collects each tooltip. */
async function tooltips(label: string, count: number) {
  const chart = screen.getByRole("application", { name: label });
  const text = () => chart.parentElement?.querySelector(".recharts-tooltip-wrapper")?.textContent ?? "";
  const result = [];

  act(() => chart.focus());
  result.push(text());
  for (let index = 1; index < count; index++) {
    fireEvent.keyDown(chart, { key: "ArrowRight" });
    await act(async () => {});
    result.push(text());
  }

  return result;
}

it.each(["ADMIN", "MEMBER"])("%s restaurant audiences see review metrics while panel usage and health remain platform-only", async (role) => {
  await restaurant(role);
  expect(screen.queryByText(/Korzystanie z panelu|Stan systemu|Otwarcia kolejki/)).toBeNull();
});

it("the platform audience sees panel usage and system health", async () => {
  fakeApi({ "GET /api/platform-analytics/v1/report": () => report });
  renderWithSWR(<PlatformAnalytics accountId="staff" onAccessLost={() => {}} />);
  expect(await screen.findByText("Korzystanie z panelu")).toBeTruthy();
  expect(screen.getByText("Stan systemu")).toBeTruthy();
});

it("pre-collection days are empty, recorded zero days remain zero, and today retains its details", async () => {
  await restaurant();
  const [beforeCollection, recordedZero, today] = await tooltips("Przyjęte zamówienia według dnia", 3);

  expect(beforeCollection).toBe("");
  expect(recordedZero).toMatch(/Przyjęte\s*0/);
  expect(today).toMatch(/Dzień w toku/);
  expect(today).toMatch(/Przyjęte\s*4/);
  expect(today).toMatch(/Zakończone\s*2/);
});

it("a report without recorded hours shows every day as empty", async () => {
  await restaurant("ADMIN", { ...report, firstRecordedHour: null });
  expect(await tooltips("Przyjęte zamówienia według dnia", 3)).toEqual(["", "", ""]);
});

it("location comparison uses completed orders and keeps full long names in details", async () => {
  const name = "Lokal o bardzo długiej nazwie — Kraków";

  await restaurant("ADMIN", { ...report, locations: [{ locationId, name, transitions: { IN_PREPARATION: 20, COMPLETED: 8 } }] });
  const [details] = await tooltips("Zakończone zamówienia według lokalu", 1);

  expect(details).toContain(name);
  expect(details).toMatch(/Zakończone\s*8/);
});

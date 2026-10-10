import "fake-indexeddb/auto";

import { beforeEach, expect, it } from "vitest";

import {
  applyReviewPush,
  consumeReviewInvitation,
  readReviewReferences,
  rememberReviewReference,
} from "@/src/pwa/storage";
import {
  applyPushTransition,
  readTrackedOrder,
  rememberLastStableDestination,
  readLastStableDestination,
  rememberTrackedOrder,
  updateNotificationMetadata,
} from "@/src/pwa/storage";

const reference = "930fccf0-c771-4c46-b4fd-ee59ce6e11cc";
const eventId = "060f9c9d-1762-4fd2-a5a8-c11ddc00ff21";

beforeEach(async () => {
  await new Promise<void>((resolve, reject) => {
    const request = indexedDB.deleteDatabase("kairos-customer");
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
});

it("preserves review recovery after completion cleanup without changing the current destination", async () => {
  await rememberLastStableDestination({ kind: "home" });
  await updateNotificationMetadata({ notificationsEnabled: true, enrolledTrackingReferences: [reference] });
  await rememberTrackedOrder({ trackingReference: reference, label: "42", status: "READY", updatedAt: "2026-10-10T10:00:00Z" });
  await applyPushTransition({ eventId, trackingReference: reference, status: "COMPLETED", transitionedAt: "2026-10-10T10:01:00Z" });
  expect(await readTrackedOrder(reference)).toBeNull();
  expect(await readReviewReferences()).toContain(reference);
  expect(await readLastStableDestination()).toEqual({ kind: "home" });
});

it("deduplicates review pushes and suppresses them after dismissal or notification opt-out", async () => {
  await updateNotificationMetadata({ notificationsEnabled: true });
  expect(await applyReviewPush(reference)).toBe(true);
  expect(await applyReviewPush(reference)).toBe(false);
  await consumeReviewInvitation(reference);
  await rememberReviewReference(reference);
  expect(await readReviewReferences()).not.toContain(reference);
  expect(await applyReviewPush(reference)).toBe(false);
  await updateNotificationMetadata({ notificationsEnabled: false });
  expect(await applyReviewPush(eventId)).toBe(false);
});

it("recovers completion through REST on return and keeps invitations hidden until due", async () => {
  const { vi } = await import("vitest");
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.test");
  const { readInvitations } = await import("@/src/pwa/review-invitations");
  const now = vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-10-10T10:00:00Z"));
  const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({
    label: "42", status: "COMPLETED", updatedAt: "2026-10-10T10:00:00Z",
    reviewInvitation: { dueAt: "2026-10-10T10:30:00Z", locationName: "Cafe", googleReviewUrl: "https://g.page/r/example/review" },
  })));
  vi.stubGlobal("fetch", fetchMock);
  try {
    await rememberTrackedOrder({ trackingReference: reference, label: "42", status: "READY", updatedAt: "2026-10-10T09:55:00Z" });
    expect((await readInvitations())[0]?.isDue).toBe(false);
    expect(await readTrackedOrder(reference)).toBeNull();
    now.mockReturnValue(Date.parse("2026-10-10T10:30:00Z"));
    expect((await readInvitations())[0]?.isDue).toBe(true);
    await consumeReviewInvitation(reference);
    expect(await readInvitations()).toEqual([]);
  } finally {
    now.mockRestore();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  }
});

it("suppresses revoked invitations and never shows cached eligibility when offline", async () => {
  const { vi } = await import("vitest");
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.test");
  const { readInvitations } = await import("@/src/pwa/review-invitations");
  await rememberReviewReference(reference);
  const fetchMock = vi.fn().mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValue(new Response(JSON.stringify({ label: "42", status: "COMPLETED", updatedAt: "2026-10-10T10:00:00Z", reviewInvitation: null })));
  vi.stubGlobal("fetch", fetchMock);
  try {
    expect(await readInvitations()).toEqual([]);
    expect(await readReviewReferences()).toContain(reference);
    expect(await readInvitations()).toEqual([]);
    expect(await readReviewReferences()).not.toContain(reference);
  } finally {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  }
});

it("hides a cached due invitation while offline", async () => {
  const { vi } = await import("vitest");
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "https://api.kairos.test");
  const { selectReviewInvitation } = await import("@/src/pwa/review-invitations");
  const cached = [{ trackingReference: reference, isDue: true, invitation: {
    dueAt: "2026-10-10T10:30:00Z", locationName: "Cafe", googleReviewUrl: "https://g.page/r/example/review",
  } }];
  expect(selectReviewInvitation(cached, new Set(), false)).toBeUndefined();
  expect(selectReviewInvitation(cached, new Set(), true)).toEqual(cached[0]);
  vi.unstubAllEnvs();
});

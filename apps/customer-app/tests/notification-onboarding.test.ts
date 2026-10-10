import { afterEach, describe, expect, it, vi } from "vitest";

import {
  isAppleMobile,
  requiresNotificationInstallation,
  shouldShowNotificationGuide,
  readGuideDismissal,
  rememberGuideDismissal,
} from "@/src/pwa/notification-onboarding";

afterEach(() => vi.unstubAllGlobals());

describe("notification onboarding", () => {
  it("remembers automatic installation-guide dismissal but allows reopening from the bell", () => {
    const context = { state: "installation-required" as const, installationRequired: true, dismissed: true };
    expect(shouldShowNotificationGuide({ ...context, automatic: true })).toBe(false);
    expect(shouldShowNotificationGuide({ ...context, automatic: false })).toBe(true);
  });

  it.each(["disabled", "blocked", "unsupported", "error", "enabled"] as const)("never opens a popup for %s when installation is unnecessary", (state) => {
    expect(shouldShowNotificationGuide({ state, installationRequired: false, dismissed: false, automatic: true })).toBe(false);
    expect(shouldShowNotificationGuide({ state, installationRequired: false, dismissed: false, automatic: false })).toBe(false);
  });

  it.each(["loading", "enabled"] as const)("does not interrupt %s notifications", (state) => {
    expect(shouldShowNotificationGuide({ state, installationRequired: true, dismissed: false, automatic: true })).toBe(false);
  });

  it("remembers dismissal across reads without storing an order identifier", () => {
    const values = new Map<string, string>();
    vi.stubGlobal("window", { localStorage: { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value) } });
    expect(readGuideDismissal()).toBe(false);
    rememberGuideDismissal();
    expect(readGuideDismissal()).toBe(true);
    expect(Array.from(values.values())).toEqual(["1"]);
  });

  it("tolerates inaccessible local storage", () => {
    vi.stubGlobal("window", { get localStorage() { throw new Error("unavailable"); } });
    expect(readGuideDismissal()).toBe(false);
    expect(() => rememberGuideDismissal()).not.toThrow();
  });

  it("guides Apple installation before Push APIs become available", () => {
    vi.stubGlobal("navigator", { userAgent: "iPhone", maxTouchPoints: 5 });
    vi.stubGlobal("window", { matchMedia: () => ({ matches: false }) });
    expect(requiresNotificationInstallation()).toBe(true);
  });

  it.each(["display-mode", "Safari standalone"])("accepts installed Apple apps through %s", (mode) => {
    vi.stubGlobal("navigator", { userAgent: "iPad", maxTouchPoints: 5, standalone: mode === "Safari standalone" });
    vi.stubGlobal("window", { matchMedia: () => ({ matches: mode === "display-mode" }) });
    expect(requiresNotificationInstallation()).toBe(false);
  });

  it("recognizes desktop-mode iPads without treating Macs as iPads", () => {
    expect(isAppleMobile({ userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)", maxTouchPoints: 5 })).toBe(true);
    expect(isAppleMobile({ userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)", maxTouchPoints: 0 })).toBe(false);
    expect(isAppleMobile({ userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 18)", maxTouchPoints: 5 })).toBe(true);
  });
});

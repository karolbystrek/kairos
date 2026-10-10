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
  it("suppresses dismissed automatic onboarding but reopens it from the bell", () => {
    const context = { state: "disabled" as const, permission: "default" as const, installationRequired: false, dismissed: true };
    expect(shouldShowNotificationGuide({ ...context, automatic: true })).toBe(false);
    expect(shouldShowNotificationGuide({ ...context, automatic: false })).toBe(true);
  });

  it("enables directly when permission is granted without required installation", () => {
    expect(shouldShowNotificationGuide({ state: "disabled", permission: "granted", installationRequired: false, dismissed: false, automatic: false })).toBe(false);
    expect(shouldShowNotificationGuide({ state: "disabled", permission: "default", installationRequired: false, dismissed: false, automatic: false })).toBe(true);
  });

  it("enables directly from an installed app even before permission is granted", () => {
    expect(shouldShowNotificationGuide({ state: "disabled", permission: "default", installed: true, installationRequired: false, dismissed: true, automatic: false })).toBe(false);
  });

  it("uses restored permission immediately rather than stale blocked state", () => {
    expect(shouldShowNotificationGuide({ state: "blocked", permission: "granted", installationRequired: false, dismissed: true, automatic: false })).toBe(false);
    expect(shouldShowNotificationGuide({ state: "blocked", permission: "denied", installed: true, installationRequired: false, dismissed: true, automatic: false })).toBe(true);
  });

  it("lets an installed app request permission after blocked permission is reset to Ask", () => {
    expect(shouldShowNotificationGuide({ state: "blocked", permission: "default", installed: true, installationRequired: false, dismissed: true, automatic: false })).toBe(false);
    expect(shouldShowNotificationGuide({ state: "disabled", permission: "denied", installed: true, installationRequired: false, dismissed: true, automatic: false })).toBe(true);
  });

  it("explains required installation even when permission is granted", () => {
    expect(shouldShowNotificationGuide({ state: "installation-required", permission: "granted", installationRequired: true, dismissed: false, automatic: false })).toBe(true);
  });

  it.each(["blocked", "unsupported"] as const)("explains %s rather than requesting permission", (state) => {
    expect(shouldShowNotificationGuide({ state, permission: undefined, installationRequired: false, dismissed: false, automatic: false })).toBe(true);
  });

  it.each(["loading", "enabled"] as const)("does not interrupt %s notifications", (state) => {
    expect(shouldShowNotificationGuide({ state, permission: "default", installationRequired: false, dismissed: false, automatic: true })).toBe(false);
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

import type { ReactElement } from "react";

import { beforeEach, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  record: vi.fn(), consume: vi.fn().mockResolvedValue(undefined), open: vi.fn(),
  reference: "930fccf0-c771-4c46-b4fd-ee59ce6e11cc",
}));

vi.mock("react", async (original) => ({
  ...await original<typeof import("react")>(),
  useEffect: () => {},
  useRef: (current: unknown) => ({ current }),
  useState: (initial: () => unknown) => [initial(), vi.fn()],
  useSyncExternalStore: () => true,
}));
vi.mock("swr", () => ({ default: () => ({ data: [{
  trackingReference: mocks.reference, isDue: true,
  invitation: { googleReviewUrl: "https://g.page/r/cafe/review", locationName: "Cafe" },
}], mutate: vi.fn() }) }));
vi.mock("@/src/api/orders", () => ({ recordReviewAction: mocks.record }));
vi.mock("@/src/pwa/storage", () => ({ consumeReviewInvitation: mocks.consume }));
vi.mock("@/src/pwa/review-invitations", async (original) => ({
  ...await original<typeof import("@/src/pwa/review-invitations")>(),
  readInvitations: vi.fn(),
}));

import { ReviewInvitation } from "@/src/pwa/review-invitation";

function elements(node: unknown): ReactElement<Record<string, unknown>>[] {
  if (!node || typeof node !== "object") return [];
  if (Array.isArray(node)) return node.flatMap(elements);
  const element = node as ReactElement<Record<string, unknown>>;
  return [element, ...elements(element.props?.children)];
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("window", { open: mocks.open });
});

it("counts a Google click without a dismissal even if a close callback also fires", () => {
  const modal = ReviewInvitation();
  const button = elements(modal).find(node => node.props.onPress && !node.props["aria-label"])!;
  (button.props.onPress as () => void)();
  modal.props.onOpenChange(false);
  expect(mocks.open).toHaveBeenCalledWith("https://g.page/r/cafe/review", "_blank", "noopener,noreferrer");
  expect(mocks.record.mock.calls).toEqual([[mocks.reference, "review_link_clicked"]]);
  expect(mocks.consume).toHaveBeenCalledTimes(1);
});

it("counts explicit modal and button closes once and consumes the invitation", () => {
  const modal = ReviewInvitation();
  const button = elements(modal).find(node => node.props["aria-label"] === "Zamknij")!;
  (button.props.onPress as () => void)();
  modal.props.onOpenChange(false);
  expect(mocks.record.mock.calls).toEqual([[mocks.reference, "review_prompt_dismissed"]]);
  expect(mocks.consume).toHaveBeenCalledWith(mocks.reference);
});

it("counts the modal close callback used by backdrop, Escape and X", () => {
  const modal = ReviewInvitation();
  modal.props.onOpenChange(false);
  expect(mocks.record.mock.calls).toEqual([[mocks.reference, "review_prompt_dismissed"]]);
});

it("does not count merely rendering a prompt", () => {
  ReviewInvitation();
  expect(mocks.record).not.toHaveBeenCalled();
  expect(mocks.consume).not.toHaveBeenCalled();
});

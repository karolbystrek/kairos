import { describe, expect, it } from "vitest";

import { createOrderState } from "@/src/orders/order-state";

const order = (status: "IN_PREPARATION" | "READY" | "COMPLETED" | "CANCELED") => ({
  number: 12, label: "A12", status, updatedAt: "2026-10-12T10:00:00Z",
});

describe("customer state reconciliation", () => {
  it("does not regress an order retained in SWR across page mounts", () => {
    const state = createOrderState(order("READY"));
    expect(state.accept(order("IN_PREPARATION")).status).toBe("READY");
  });
  it("keeps terminal state under delayed REST, stream snapshots, events and push", () => {
    const state = createOrderState();
    state.accept(order("IN_PREPARATION"));
    state.transition({ status: "READY", updatedAt: "2026-10-12T09:00:00Z" });
    expect(state.accept(order("IN_PREPARATION")).status).toBe("READY");
    state.accept(order("COMPLETED"));
    state.transition(order("READY"));
    expect(state.accept(order("CANCELED")).status).toBe("COMPLETED");
    expect(state.accept(order("IN_PREPARATION")).status).toBe("COMPLETED");
  });

  it("retains a push arriving before the first REST representation", () => {
    const state = createOrderState();
    state.transition(order("COMPLETED"));
    expect(state.accept(order("IN_PREPARATION"))).toEqual(order("COMPLETED"));
  });

  it("preserves the first terminal push even before a full representation arrives", () => {
    const state = createOrderState();
    state.transition(order("CANCELED"));
    expect(state.accept(order("COMPLETED")).status).toBe("CANCELED");
  });

  it("accepts same-status review metadata without undoing a newer transition time", () => {
    const state = createOrderState();
    state.accept({ ...order("COMPLETED"), updatedAt: "2026-10-12T11:00:00Z" });
    const reviewInvitation = { dueAt: "2026-10-12T12:00:00Z", locationName: "Cafe", googleReviewUrl: "https://g.page/review" };
    expect(state.accept({ ...order("COMPLETED"), reviewInvitation })).toEqual({
      ...order("COMPLETED"), updatedAt: "2026-10-12T11:00:00Z", reviewInvitation,
    });
  });
});

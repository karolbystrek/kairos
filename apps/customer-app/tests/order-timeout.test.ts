import { afterEach, expect, it, vi } from "vitest";

import { getTrackedOrder } from "@/src/api/orders";

afterEach(() => vi.restoreAllMocks());

it("a stalled order request is aborted after 10 s so the stored snapshot is shown", async () => {
  // Timer-backed signals ignore fake timers, so the 10 s expiry is made immediate.
  const timeout = vi
    .spyOn(AbortSignal, "timeout")
    .mockImplementation(() => AbortSignal.abort(new DOMException("", "TimeoutError")));

  vi.stubGlobal(
    "fetch",
    async (_: unknown, init: RequestInit) =>
      init.signal?.aborted ? Promise.reject(init.signal.reason) : new Promise(() => {}),
  );
  await expect(
    getTrackedOrder("930fccf0-c771-4c46-b4fd-ee59ce6e11cc"),
  ).rejects.toMatchObject({ name: "TimeoutError" });
  expect(timeout).toHaveBeenCalledWith(10_000);
  vi.unstubAllGlobals();
});

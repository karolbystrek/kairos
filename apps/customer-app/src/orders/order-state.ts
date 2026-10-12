import type { CustomerOrder } from "@/src/api/orders";

import { canApplyStatus } from "@/src/orders/order-status";

type Transition = Pick<CustomerOrder, "status" | "updatedAt">;

// One state gate for REST, SSE and Push; timestamps cannot order status transitions.
export function createOrderState(initial?: CustomerOrder) {
  let current = initial;
  let pending: Transition | undefined;

  function accept(candidate: CustomerOrder): CustomerOrder {
    if (!current && pending) {
      current = { ...candidate, ...pending };
      pending = undefined;
    }
    if (current && !canApplyStatus(current.status, candidate.status))
      return current;
    if (
      current?.status === candidate.status &&
      Date.parse(current.updatedAt) > Date.parse(candidate.updatedAt)
    ) {
      candidate = { ...candidate, updatedAt: current.updatedAt };
    }
    current = candidate;

    return current;
  }

  function transition(candidate: Transition): CustomerOrder | undefined {
    if (current) return accept({ ...current, ...candidate });
    if (!pending || canApplyStatus(pending.status, candidate.status))
      pending = candidate;

    return undefined;
  }

  return { accept, transition };
}

import { z } from "zod";

export const orderStatusSchema = z.enum([
  "IN_PREPARATION",
  "READY",
  "COMPLETED",
  "CANCELED",
]);

export type OrderStatus = z.infer<typeof orderStatusSchema>;

export const orderStatusLabels: Record<OrderStatus, string> = {
  IN_PREPARATION: "W przygotowaniu",
  READY: "Gotowe do odbioru",
  COMPLETED: "Zrealizowane",
  CANCELED: "Anulowane",
};

export function isActiveOrderStatus(status: OrderStatus | undefined): boolean {
  return status === "IN_PREPARATION" || status === "READY";
}

export function canApplyStatus(
  current: OrderStatus,
  candidate: OrderStatus,
): boolean {
  return (
    current === candidate ||
    (current === "IN_PREPARATION" && candidate !== "IN_PREPARATION") ||
    (current === "READY" &&
      (candidate === "COMPLETED" || candidate === "CANCELED"))
  );
}

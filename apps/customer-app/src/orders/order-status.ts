import { z } from "zod";

export const orderStatusSchema = z.enum([
  "IN_PREPARATION",
  "READY",
  "COMPLETED",
  "CANCELED",
]);

export type OrderStatus = z.infer<typeof orderStatusSchema>;

export const orderStatusLabels: Record<OrderStatus, string> = {
  IN_PREPARATION: "In preparation",
  READY: "Ready for pickup",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

export function isActiveOrderStatus(status: OrderStatus | undefined): boolean {
  return status === "IN_PREPARATION" || status === "READY";
}

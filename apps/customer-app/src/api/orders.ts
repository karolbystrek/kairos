import { z } from "zod";

import { orderStatusSchema } from "@/src/orders/order-status";

export const customerOrderSchema = z.object({
  label: z.string(),
  status: orderStatusSchema,
  updatedAt: z.iso.datetime({ offset: true }),
});

export const orderStatusChangedEventSchema = z.object({
  trackingReference: z.uuid(),
  status: orderStatusSchema,
  updatedAt: z.iso.datetime({ offset: true }),
});

export type { OrderStatus } from "@/src/orders/order-status";
export type CustomerOrder = z.infer<typeof customerOrderSchema>;

export class ApiError extends Error {
  constructor(public readonly status: number) {
    super(`The API returned ${status}.`);
  }
}

export async function getTrackedOrder(
  trackingReference: string,
): Promise<CustomerOrder> {
  const response = await fetch(
    `/api/tracked-orders/v1/${encodeURIComponent(trackingReference)}`,
  );

  if (!response.ok) {
    throw new ApiError(response.status);
  }

  return customerOrderSchema.parse(await response.json());
}

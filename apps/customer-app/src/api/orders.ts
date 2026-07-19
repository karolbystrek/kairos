import { z } from "zod";

export const orderStatusSchema = z.enum([
  "CREATED",
  "IN_PREPARATION",
  "READY",
  "COMPLETED",
  "CANCELED",
]);

export const customerOrderSchema = z.object({
  label: z.string(),
  status: orderStatusSchema,
  updatedAt: z.iso.datetime({ offset: true }),
});

export type OrderStatus = z.infer<typeof orderStatusSchema>;
export type CustomerOrder = z.infer<typeof customerOrderSchema>;

export async function getTrackedOrder(
  trackingReference: string,
): Promise<CustomerOrder> {
  const response = await fetch(
    `/api/order-tracking/${encodeURIComponent(trackingReference)}`,
  );

  if (!response.ok) {
    throw new Error(`The API returned ${response.status}.`);
  }

  return customerOrderSchema.parse(await response.json());
}

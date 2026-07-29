import { z } from "zod";

import { request } from "./api-fetch";

export const orderStatusSchema = z.enum([
  "IN_PREPARATION",
  "READY",
  "COMPLETED",
  "CANCELED",
]);

export const activeOrderStatusSchema = z.enum(["IN_PREPARATION", "READY"]);

export const staffOrderSchema = z.object({
  id: z.uuid(),
  locationId: z.uuid(),
  trackingReference: z.uuid(),
  label: z.string(),
  status: orderStatusSchema,
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

const staffOrdersSchema = z.array(staffOrderSchema);

export const customOrderLabelSchema = z
  .string()
  .trim()
  .min(1, "Enter a custom label.")
  .refine(
    (label) => Array.from(label).length <= 32,
    "Use at most 32 characters.",
  )
  .refine(
    (label) => !/[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(label),
    "Use one line of text.",
  );

export const createOrderInputSchema = z.discriminatedUnion("mode", [
  z.object({ mode: z.literal("AUTO") }),
  z.object({
    mode: z.literal("CUSTOM"),
    label: customOrderLabelSchema,
  }),
]);

export type OrderStatus = z.infer<typeof orderStatusSchema>;
export type ActiveOrderStatus = z.infer<typeof activeOrderStatusSchema>;
export type StaffOrder = z.infer<typeof staffOrderSchema>;
export type CreateOrderInput = z.infer<typeof createOrderInputSchema>;

export function listOrders(
  locationId?: string,
  status?: ActiveOrderStatus,
): Promise<StaffOrder[]> {
  const query = new URLSearchParams();

  if (locationId) query.set("locationId", locationId);
  if (status) query.set("status", status);

  const queryString = query.toString();

  return request(
    `/api/orders/v1${queryString ? `?${queryString}` : ""}`,
    staffOrdersSchema,
  );
}

export function createOrder(
  locationId: string,
  input: CreateOrderInput,
): Promise<StaffOrder> {
  const parsedInput = createOrderInputSchema.parse(input);
  const body =
    parsedInput.mode === "CUSTOM" ? { label: parsedInput.label } : {};

  return request("/api/orders/v1", staffOrderSchema, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ locationId, ...body }),
  });
}

export function updateOrderStatus(
  orderId: string,
  status: OrderStatus,
): Promise<StaffOrder> {
  return request(
    `/api/orders/v1/${encodeURIComponent(orderId)}/status`,
    staffOrderSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    },
  );
}

import { z } from "zod";

import { request } from "./api-fetch";

export const orderStatusSchema = z.enum([
  "CREATED",
  "IN_PREPARATION",
  "READY",
  "COMPLETED",
  "CANCELED",
]);

export const locationSchema = z.object({
  id: z.uuid(),
});

export const staffOrderSchema = z.object({
  id: z.uuid(),
  locationId: z.uuid(),
  trackingReference: z.uuid(),
  status: orderStatusSchema,
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

const locationsSchema = z.array(locationSchema);
const staffOrdersSchema = z.array(staffOrderSchema);

export type OrderStatus = z.infer<typeof orderStatusSchema>;
export type Location = z.infer<typeof locationSchema>;
export type StaffOrder = z.infer<typeof staffOrderSchema>;

export function listLocations(): Promise<Location[]> {
  return request("/api/locations", locationsSchema);
}

export function listOrders(locationId: string): Promise<StaffOrder[]> {
  return request(
    `/api/locations/${encodeURIComponent(locationId)}/orders`,
    staffOrdersSchema,
  );
}

export function createOrder(locationId: string): Promise<StaffOrder> {
  return request(
    `/api/locations/${encodeURIComponent(locationId)}/orders`,
    staffOrderSchema,
    {
      method: "POST",
    },
  );
}

export function updateOrderStatus(
  orderId: string,
  status: OrderStatus,
): Promise<StaffOrder> {
  return request(
    `/api/orders/${encodeURIComponent(orderId)}/status`,
    staffOrderSchema,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    },
  );
}

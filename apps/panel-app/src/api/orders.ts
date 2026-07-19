import { z } from "zod";

export const orderStatusSchema = z.enum([
  "CREATED",
  "IN_PREPARATION",
  "READY",
  "COMPLETED",
  "CANCELED",
]);

export const locationSchema = z.object({
  id: z.uuid(),
  name: z.string(),
});

export const staffOrderSchema = z.object({
  id: z.uuid(),
  locationId: z.uuid(),
  trackingReference: z.uuid(),
  label: z.string(),
  status: orderStatusSchema,
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

const locationsSchema = z.array(locationSchema);
const staffOrdersSchema = z.array(staffOrderSchema);
const createOrderInputSchema = z.object({
  label: z.string().trim().min(1, "Order label is required").max(80),
});

export type OrderStatus = z.infer<typeof orderStatusSchema>;
export type Location = z.infer<typeof locationSchema>;
export type StaffOrder = z.infer<typeof staffOrderSchema>;

export class ApiError extends Error {
  constructor(public readonly status: number) {
    super(`The API returned ${status}.`);
  }
}

async function request<T>(
  url: string,
  schema: z.ZodType<T>,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(url, init);

  if (!response.ok) {
    throw new ApiError(response.status);
  }

  return schema.parse(await response.json());
}

export function listLocations(): Promise<Location[]> {
  return request("/api/locations", locationsSchema);
}

export function listOrders(locationId: string): Promise<StaffOrder[]> {
  return request(
    `/api/locations/${encodeURIComponent(locationId)}/orders`,
    staffOrdersSchema,
  );
}

export function createOrder(
  locationId: string,
  label: string,
): Promise<StaffOrder> {
  const input = createOrderInputSchema.parse({ label });

  return request(
    `/api/locations/${encodeURIComponent(locationId)}/orders`,
    staffOrderSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
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

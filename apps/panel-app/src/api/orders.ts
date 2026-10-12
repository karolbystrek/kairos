import { z } from "zod";

import { request } from "./api-fetch";

import { apiBaseUrl } from "@/src/config/public-environment";

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

export function subscribeToOrderQueue(
  locationId: string | undefined,
  onSnapshot: (orders: StaffOrder[]) => void,
  onDisconnected: () => void,
): () => void {
  const url = new URL("/api/orders/v1/events", apiBaseUrl);

  if (locationId) url.searchParams.set("locationId", locationId);
  const events = new EventSource(url, { withCredentials: true });

  events.addEventListener("order-queue", (event) => {
    try {
      onSnapshot(staffOrdersSchema.parse(JSON.parse(event.data)));
    } catch {
      onDisconnected();
    }
  });
  events.onerror = onDisconnected;

  return () => events.close();
}

export const customOrderLabelSchema = z
  .string()
  .trim()
  .min(1, "Podaj własną etykietę.")
  .refine(
    (label) => Array.from(label).length <= 32,
    "Użyj maksymalnie 32 znaków.",
  )
  .refine(
    (label) => !/[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(label),
    "Użyj jednej linii tekstu.",
  );

export const maximumOrderLabelLength = 200;
// Reserves room for the automatic daily number and separator the API prepends.
const automaticPrefixReserve = Array.from("9999 · ").length;

export const orderItemSchema = z.object({
  name: z.string().min(1),
  quantity: z.number().int().min(1).max(99),
});

export type OrderItem = z.infer<typeof orderItemSchema>;

// Mirrors the API label format so staff can preview and size their selection.
export function describeOrderItems(items: OrderItem[]): string {
  return items
    .map((item) =>
      item.quantity === 1 ? item.name : `${item.quantity}× ${item.name}`,
    )
    .join(", ");
}

export const createOrderInputSchema = z.discriminatedUnion("mode", [
  z.object({ mode: z.literal("AUTO") }),
  z.object({
    mode: z.literal("CUSTOM"),
    label: customOrderLabelSchema,
  }),
  z.object({
    mode: z.literal("ITEMS"),
    items: z
      .array(orderItemSchema)
      .min(1)
      .refine(
        (items) =>
          Array.from(describeOrderItems(items)).length +
            automaticPrefixReserve <=
          maximumOrderLabelLength,
        "Wybrane pozycje nie zmieszczą się w etykiecie. Wybierz mniej pozycji.",
      ),
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
    parsedInput.mode === "CUSTOM"
      ? { label: parsedInput.label }
      : parsedInput.mode === "ITEMS"
        ? { items: parsedInput.items }
        : {};

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

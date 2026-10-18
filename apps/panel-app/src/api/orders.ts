import { z } from "zod";

import { request } from "./api-fetch";

const orderStatusSchema = z.enum([
  "IN_PREPARATION",
  "READY",
  "COMPLETED",
  "CANCELED",
  "EXPIRED",
]);

export const activeOrderStatusSchema = z.enum(["IN_PREPARATION", "READY"]);

const orderItemSchema = z.object({
  name: z.string().min(1),
  quantity: z.number().int().min(1).max(99),
});

const staffOrderSchema = z.object({
  id: z.uuid(),
  locationId: z.uuid(),
  trackingReference: z.uuid(),
  number: z.number().int().positive(),
  label: z.string().nullable(),
  items: z.array(orderItemSchema),
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
  const url = new URL("/api/orders/v1/events", location.origin);

  if (locationId) url.searchParams.set("locationId", locationId);
  let events: EventSource;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let delay = 2_000;
  let attempts = 0;
  const connect = () => {
    events = new EventSource(url);
    events.addEventListener("order-queue", (event) => {
      // The server sends a full snapshot on every (re)connection.
      delay = 2_000;
      attempts = 0;
      try {
        onSnapshot(staffOrdersSchema.parse(JSON.parse(event.data)));
      } catch {
        onDisconnected();
      }
    });
    events.onerror = () => {
      onDisconnected();
      // A closed source (HTTP error) never retries by itself.
      if (events.readyState !== EventSource.CLOSED) return;
      events.close();
      // Persistent refusals (401, 403) leave the queue to the polling fallback.
      if (++attempts > 10) return;
      // Jitter keeps clients from reconnecting in lockstep after an outage.
      timer = setTimeout(connect, delay * (0.5 + Math.random()));
      delay = Math.min(delay * 2, 30_000);
    };
  };

  connect();

  return () => {
    clearTimeout(timer);
    events.close();
  };
}

export const maximumOrderLabelLength = 80;

const orderLabelSchema = z
  .string()
  .trim()
  .refine(
    (label) => Array.from(label).length <= maximumOrderLabelLength,
    `Użyj maksymalnie ${maximumOrderLabelLength} znaków.`,
  )
  .refine(
    (label) => !/[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(label),
    "Użyj jednej linii tekstu.",
  );

type OrderItem = z.infer<typeof orderItemSchema>;

// Prefills the editable label from chosen items, shortened to the label limit.
export function describeOrderItems(items: OrderItem[]): string {
  const characters = Array.from(
    items
      .map((item) =>
        item.quantity === 1 ? item.name : `${item.quantity}× ${item.name}`,
      )
      .join(", "),
  );

  return characters.length <= maximumOrderLabelLength
    ? characters.join("")
    : `${characters
        .slice(0, maximumOrderLabelLength - 1)
        .join("")
        .trimEnd()}…`;
}

export const createOrderInputSchema = z.object({
  label: orderLabelSchema,
  items: z.array(orderItemSchema).max(100),
});

export type OrderStatus = z.infer<typeof orderStatusSchema>;
type ActiveOrderStatus = z.infer<typeof activeOrderStatusSchema>;
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
  const { items, label } = createOrderInputSchema.parse(input);

  return request("/api/orders/v1", staffOrderSchema, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ locationId, label: label || undefined, items }),
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

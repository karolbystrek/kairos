export type OrderStatus =
  | "CREATED"
  | "IN_PREPARATION"
  | "READY"
  | "COMPLETED"
  | "CANCELED";

export interface CustomerOrder {
  label: string;
  status: OrderStatus;
  updatedAt: string;
}

export async function getTrackedOrder(
  trackingReference: string,
): Promise<CustomerOrder> {
  const response = await fetch(
    `/api/order-tracking/${encodeURIComponent(trackingReference)}`,
  );

  if (!response.ok) {
    throw new Error(`The API returned ${response.status}.`);
  }

  return response.json() as Promise<CustomerOrder>;
}

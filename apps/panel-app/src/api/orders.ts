export type OrderStatus =
  | "CREATED"
  | "IN_PREPARATION"
  | "READY"
  | "COMPLETED"
  | "CANCELED";

export interface Location {
  id: string;
  name: string;
}

export interface StaffOrder {
  id: string;
  locationId: string;
  trackingReference: string;
  label: string;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
}

export class ApiError extends Error {
  constructor(public readonly status: number) {
    super(`The API returned ${status}.`);
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);

  if (!response.ok) {
    throw new ApiError(response.status);
  }

  return response.json() as Promise<T>;
}

export function listLocations(): Promise<Location[]> {
  return request("/api/locations");
}

export function listOrders(locationId: string): Promise<StaffOrder[]> {
  return request(`/api/locations/${encodeURIComponent(locationId)}/orders`);
}

export function createOrder(
  locationId: string,
  label: string,
): Promise<StaffOrder> {
  return request(`/api/locations/${encodeURIComponent(locationId)}/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ label }),
  });
}

export function updateOrderStatus(
  orderId: string,
  status: OrderStatus,
): Promise<StaffOrder> {
  return request(`/api/orders/${encodeURIComponent(orderId)}/status`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
}

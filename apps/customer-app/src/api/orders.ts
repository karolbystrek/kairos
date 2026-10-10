import { z } from "zod";

import { apiUrl } from "@/src/api/api-url";
import { orderStatusSchema } from "@/src/orders/order-status";

export const reviewInvitationSchema = z.object({
  dueAt: z.iso.datetime({ offset: true }),
  locationName: z.string(),
  googleReviewUrl: z.url().refine((value) => {
    const url = new URL(value);

    return (
      url.protocol === "https:" &&
      !url.username &&
      !url.password &&
      [
        "g.page",
        "maps.app.goo.gl",
        "search.google.com",
        "www.google.com",
        "maps.google.com",
      ].includes(url.hostname)
    );
  }),
});

export const customerOrderSchema = z.object({
  label: z.string(),
  status: orderStatusSchema,
  updatedAt: z.iso.datetime({ offset: true }),
  reviewInvitation: reviewInvitationSchema.nullish(),
});

export const orderStatusChangedEventSchema = z.object({
  eventId: z.uuid(),
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
    apiUrl(`/api/tracked-orders/v1/${encodeURIComponent(trackingReference)}`),
  );

  if (!response.ok) {
    throw new ApiError(response.status);
  }

  return customerOrderSchema.parse(await response.json());
}

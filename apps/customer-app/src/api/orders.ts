import { z } from "zod";

import { customerMutation } from "@/src/api/customer-notifications";
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
  number: z.number().int().positive(),
  label: z.string().nullable(),
  status: orderStatusSchema,
  updatedAt: z.iso.datetime({ offset: true }),
  reviewInvitation: reviewInvitationSchema.nullish(),
});

export const orderStatusChangedEventSchema = customerOrderSchema.extend({
  eventId: z.uuid(),
  trackingReference: z.uuid(),
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

export function recordReviewAction(
  trackingReference: string,
  event: "review_link_clicked" | "review_prompt_dismissed",
) {
  void customerMutation(
    `/api/tracked-orders/v1/${encodeURIComponent(trackingReference)}/review-actions`,
    "POST",
    { event },
  ).catch(() => {});
}

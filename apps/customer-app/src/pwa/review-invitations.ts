import { ApiError, getTrackedOrder } from "@/src/api/orders";
import { updateApplicationBadge } from "@/src/pwa/badge";
import {
  consumeReviewInvitation,
  readReviewReferences,
  readTrackedOrder,
  rememberTrackedOrder,
} from "@/src/pwa/storage";

export async function readInvitations() {
  if (typeof navigator !== "undefined" && navigator.onLine === false) return [];
  const references = await readReviewReferences();
  const results = await Promise.allSettled(
    references.map(async (trackingReference) => {
      let order;

      try {
        order = await getTrackedOrder(trackingReference);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          await consumeReviewInvitation(trackingReference);
        }
        throw error;
      }
      if (order.status === "COMPLETED" || order.status === "CANCELED") {
        if (await readTrackedOrder(trackingReference)) {
          await rememberTrackedOrder({
            trackingReference,
            label: order.label,
            status: order.status,
            updatedAt: order.updatedAt,
          });
          await updateApplicationBadge();
        }
      }

      if (
        order.status === "CANCELED" ||
        (order.status === "COMPLETED" && !order.reviewInvitation)
      ) {
        await consumeReviewInvitation(trackingReference);
      }

      return { trackingReference, invitation: order.reviewInvitation };
    }),
  );

  return results
    .flatMap((result) =>
      result.status === "fulfilled" && result.value.invitation
        ? [
            {
              trackingReference: result.value.trackingReference,
              invitation: result.value.invitation,
              isDue: Date.parse(result.value.invitation.dueAt) <= Date.now(),
            },
          ]
        : [],
    )
    .sort(
      (left, right) =>
        Date.parse(left.invitation.dueAt) - Date.parse(right.invitation.dueAt),
    );
}

export function selectReviewInvitation(
  invitations: Awaited<ReturnType<typeof readInvitations>>,
  consumed: ReadonlySet<string>,
  online: boolean,
) {
  return online
    ? invitations.find(
        (item) => !consumed.has(item.trackingReference) && item.isDue,
      )
    : undefined;
}

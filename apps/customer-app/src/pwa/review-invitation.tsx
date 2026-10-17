"use client";

import { Button, Modal } from "@heroui/react";
import { StarIcon, XIcon } from "@phosphor-icons/react";
import { useEffect, useRef, useState } from "react";
import useSWR from "swr";

import { useOnline } from "@/src/pwa/use-online";
import {
  readInvitations,
  selectReviewInvitation,
} from "@/src/pwa/review-invitations";
import { consumeReviewInvitation } from "@/src/pwa/storage";
import { recordReviewAction } from "@/src/api/orders";

export function ReviewInvitation() {
  const consumedActions = useRef(new Set<string>());
  const [consumed, setConsumed] = useState<Set<string>>(() => new Set());
  const {
    data = [],
    error,
    mutate,
  } = useSWR("customer-review-invitations", readInvitations, {
    refreshInterval: (invitations) => {
      const next = invitations?.find(
        (item) => !consumed.has(item.trackingReference),
      );

      if (!next) return 0;

      return Math.min(
        2_147_483_647,
        // Once due, the reviews-changed event and focus keep the list fresh.
        next.isDue
          ? 0
          : Math.max(1000, Date.parse(next.invitation.dueAt) - Date.now()),
      );
    },
    refreshWhenHidden: false,
    shouldRetryOnError: false,
  });
  // A revalidation can finish after going offline, so gate on connectivity too.
  const online = useOnline();
  const current = selectReviewInvitation(
    online && !error ? data : [],
    consumed,
  );

  useEffect(() => {
    const changed = () => {
      void mutate();
    };

    const offline = () => {
      void mutate([], { revalidate: false });
    };

    window.addEventListener("offline", offline);
    window.addEventListener("kairos-reviews-changed", changed);
    window.addEventListener("online", changed);

    return () => {
      window.removeEventListener("offline", offline);
      window.removeEventListener("kairos-reviews-changed", changed);
      window.removeEventListener("online", changed);
    };
  }, [mutate]);

  function consume(event: "review_link_clicked" | "review_prompt_dismissed") {
    if (!current || consumedActions.current.has(current.trackingReference))
      return;
    consumedActions.current.add(current.trackingReference);
    recordReviewAction(current.trackingReference, event);
    setConsumed((previous) => new Set(previous).add(current.trackingReference));
    // Polling stops while an invitation is due; refetch to schedule the next one.
    void mutate();
    void consumeReviewInvitation(current.trackingReference).catch(() => {
      // Keep dismissal in memory if browser storage becomes unavailable.
    });
  }

  return (
    <Modal
      isOpen={Boolean(current)}
      onOpenChange={(open) => {
        if (!open) consume("review_prompt_dismissed");
      }}
    >
      <Modal.Backdrop isDismissable className="notification-guide-backdrop">
        <Modal.Container
          className="notification-guide-container"
          placement="center"
          size="sm"
        >
          <Modal.Dialog>
            <Modal.CloseTrigger aria-label="Zamknij zaproszenie do wystawienia opinii">
              <XIcon aria-hidden="true" size={20} />
            </Modal.CloseTrigger>
            <Modal.Header className="pr-8">
              <Modal.Heading>
                Podziel się opinią o {current?.invitation.locationName} w Google
              </Modal.Heading>
            </Modal.Header>
            <Modal.Footer>
              <Button
                aria-label="Zamknij"
                variant="secondary"
                onPress={() => consume("review_prompt_dismissed")}
              >
                Zamknij
              </Button>
              <Button
                onPress={() => {
                  if (current)
                    window.open(
                      current.invitation.googleReviewUrl,
                      "_blank",
                      "noopener,noreferrer",
                    );
                  consume("review_link_clicked");
                }}
              >
                <StarIcon aria-hidden="true" size={18} />
                Wystaw opinię w Google
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

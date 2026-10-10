"use client";

import { Button, Modal } from "@heroui/react";
import { X } from "lucide-react";
import { useEffect, useState, useSyncExternalStore } from "react";
import useSWR from "swr";

import {
  readInvitations,
  selectReviewInvitation,
} from "@/src/pwa/review-invitations";
import {
  consumeReviewInvitation,
  rememberReviewReference,
} from "@/src/pwa/storage";

function subscribeConnectivity(changed: () => void) {
  window.addEventListener("online", changed);
  window.addEventListener("offline", changed);

  return () => {
    window.removeEventListener("online", changed);
    window.removeEventListener("offline", changed);
  };
}

export function ReviewInvitation() {
  const online = useSyncExternalStore(
    subscribeConnectivity,
    () => navigator.onLine,
    () => false,
  );
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
        next.isDue
          ? 15000
          : Math.max(1000, Date.parse(next.invitation.dueAt) - Date.now()),
      );
    },
    refreshWhenHidden: false,
    shouldRetryOnError: false,
  });
  const current = selectReviewInvitation(data, consumed, online && !error);

  useEffect(() => {
    const reference = new URL(window.location.href).searchParams.get("review");

    if (reference) void rememberReviewReference(reference).catch(() => {});
    const changed = () => {
      void mutate();
    };

    const offline = () => {
      void mutate([], { revalidate: false });
    };

    window.addEventListener("offline", offline);
    window.addEventListener("kairos-reviews-changed", changed);
    window.addEventListener("online", changed);
    navigator.serviceWorker?.addEventListener("message", changed);

    return () => {
      window.removeEventListener("offline", offline);
      window.removeEventListener("kairos-reviews-changed", changed);
      window.removeEventListener("online", changed);
      navigator.serviceWorker?.removeEventListener("message", changed);
    };
  }, [mutate]);

  function dismiss() {
    if (!current) return;
    setConsumed(
      (previous) =>
        new Set([...Array.from(previous), current.trackingReference]),
    );
    void consumeReviewInvitation(current.trackingReference).catch(() => {
      // Keep dismissal in memory if browser storage becomes unavailable.
    });
  }

  return (
    <Modal
      isOpen={Boolean(current)}
      onOpenChange={(open) => {
        if (!open) dismiss();
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
              <X aria-hidden="true" size={20} />
            </Modal.CloseTrigger>
            <Modal.Header className="pr-8">
              <Modal.Heading>
                Podziel się opinią o {current?.invitation.locationName} w Google
              </Modal.Heading>
            </Modal.Header>
            <Modal.Footer>
              <Button variant="secondary" onPress={dismiss}>
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
                  dismiss();
                }}
              >
                Wystaw opinię w Google
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

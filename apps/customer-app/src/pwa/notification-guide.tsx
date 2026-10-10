"use client";

import { Button, Modal, Tooltip } from "@heroui/react";
import { X } from "lucide-react";

import { useCustomerNotifications } from "@/src/pwa/notification-provider";

export function NotificationGuide() {
  const { dismissGuide, guideOpen } = useCustomerNotifications();

  return (
    <Modal
      isOpen={guideOpen}
      onOpenChange={(open) => {
        if (!open) dismissGuide();
      }}
    >
      <Modal.Backdrop isDismissable>
        <Modal.Container placement="center" size="sm">
          <Modal.Dialog>
            <Tooltip delay={500}>
              <Tooltip.Trigger>
                <Modal.CloseTrigger aria-label="Dismiss notification guide">
                  <X aria-hidden="true" size={20} />
                </Modal.CloseTrigger>
              </Tooltip.Trigger>
              <Tooltip.Content>Dismiss</Tooltip.Content>
            </Tooltip>
            <Modal.Header className="pr-8">
              <Modal.Heading>Add Kairos to your Home Screen</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4">
              <p>
                To receive order notifications on iPhone or iPad, open Kairos in
                Safari, tap Share, then Add to Home Screen and Add.
              </p>
              <p>
                Open Kairos from the Home Screen and tap the bell to enable
                notifications. Requires iOS or iPadOS 16.4 or later.
              </p>
              <p className="text-muted text-sm">
                Tracking works without installation. Dismissal is remembered on
                this device; tap the bell to see these instructions again.
              </p>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={dismissGuide}>
                Got it
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

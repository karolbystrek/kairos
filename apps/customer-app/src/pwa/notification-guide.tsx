"use client";

import { Button, Modal, Tooltip } from "@heroui/react";
import { Bell, Share, SquarePlus, X } from "lucide-react";

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
      <Modal.Backdrop isDismissable className="notification-guide-backdrop">
        <Modal.Container
          className="notification-guide-container"
          placement="center"
          size="sm"
        >
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
              <Modal.Heading>Get order notifications</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <p className="text-muted">On iPhone or iPad, add Kairos first.</p>
              <ol className="notification-installation-steps">
                <li>
                  <span className="notification-installation-icon">
                    <Share aria-hidden="true" size={24} />
                  </span>
                  <div>
                    <span className="text-muted text-sm">1</span>
                    <p>Tap Share in Safari</p>
                  </div>
                </li>
                <li>
                  <span className="notification-installation-icon">
                    <SquarePlus aria-hidden="true" size={24} />
                  </span>
                  <div>
                    <span className="text-muted text-sm">2</span>
                    <p>Add to Home Screen</p>
                  </div>
                </li>
                <li>
                  <span className="notification-installation-icon">
                    <Bell aria-hidden="true" size={24} />
                  </span>
                  <div>
                    <span className="text-muted text-sm">3</span>
                    <p>Open Kairos → tap the bell</p>
                  </div>
                </li>
              </ol>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={dismissGuide}>
                Not now
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

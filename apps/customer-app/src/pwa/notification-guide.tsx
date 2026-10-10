"use client";

import { Button, Modal, Tooltip } from "@heroui/react";
import { Bell, Share, SquarePlus, X } from "lucide-react";

import { useCustomerNotifications } from "@/src/pwa/notification-provider";

export function NotificationGuide() {
  const { dismissGuide, enable, guideOpen, state } = useCustomerNotifications();
  const installationRequired = state === "installation-required";

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
                <Modal.CloseTrigger aria-label="Zamknij instrukcję powiadomień">
                  <X aria-hidden="true" size={20} />
                </Modal.CloseTrigger>
              </Tooltip.Trigger>
              <Tooltip.Content>Zamknij</Tooltip.Content>
            </Tooltip>
            <Modal.Header className="pr-8">
              <Modal.Heading>
                {installationRequired
                  ? "Dodaj Kairos do ekranu początkowego"
                  : "Dowiedz się, kiedy zamówienie będzie gotowe"}
              </Modal.Heading>
            </Modal.Header>
            {installationRequired && (
              <Modal.Body>
                <ol className="notification-installation-steps">
                  <li>
                    <span className="notification-installation-icon">
                      <Share aria-hidden="true" size={24} />
                    </span>
                    <div>
                      <span className="text-muted text-sm">1</span>
                      <p>Stuknij Udostępnij w Safari</p>
                    </div>
                  </li>
                  <li>
                    <span className="notification-installation-icon">
                      <SquarePlus aria-hidden="true" size={24} />
                    </span>
                    <div>
                      <span className="text-muted text-sm">2</span>
                      <p>Dodaj do ekranu początkowego</p>
                    </div>
                  </li>
                  <li>
                    <span className="notification-installation-icon">
                      <Bell aria-hidden="true" size={24} />
                    </span>
                    <div>
                      <span className="text-muted text-sm">3</span>
                      <p>Otwórz Kairos → stuknij dzwonek</p>
                    </div>
                  </li>
                </ol>
              </Modal.Body>
            )}
            <Modal.Footer>
              <Button variant="secondary" onPress={dismissGuide}>
                Nie teraz
              </Button>
              {!installationRequired && (
                <Button
                  variant="primary"
                  onPress={() => {
                    dismissGuide();
                    void enable();
                  }}
                >
                  Włącz powiadomienia
                </Button>
              )}
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

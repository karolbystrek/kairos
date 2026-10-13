"use client";

import { Button, Link, Modal, Tooltip } from "@heroui/react";
import {
  BellIcon,
  ExportIcon,
  PlusSquareIcon,
  XIcon,
} from "@phosphor-icons/react";

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
                  <XIcon aria-hidden="true" size={20} />
                </Modal.CloseTrigger>
              </Tooltip.Trigger>
              <Tooltip.Content>Zamknij</Tooltip.Content>
            </Tooltip>
            <Modal.Header className="pr-8">
              <Modal.Heading>
                {installationRequired
                  ? "Dodaj Kairos do ekranu początkowego"
                  : "Włączyć powiadomienia?"}
              </Modal.Heading>
            </Modal.Header>
            {!installationRequired && (
              <Modal.Body>
                <p className="text-sm text-muted">
                  Powiadomimy Cię tylko o zmianie statusu zamówienia. Możesz je
                  wyłączyć w dowolnej chwili.{" "}
                  <Link className="text-sm" href="/prywatnosc">
                    Prywatność
                  </Link>
                </p>
              </Modal.Body>
            )}
            {installationRequired && (
              <Modal.Body>
                <ol className="notification-installation-steps">
                  <li>
                    <span className="notification-installation-icon">
                      <ExportIcon aria-hidden="true" size={24} />
                    </span>
                    <div>
                      <span className="text-muted text-sm">1</span>
                      <p>Stuknij Udostępnij w Safari</p>
                    </div>
                  </li>
                  <li>
                    <span className="notification-installation-icon">
                      <PlusSquareIcon aria-hidden="true" size={24} />
                    </span>
                    <div>
                      <span className="text-muted text-sm">2</span>
                      <p>Dodaj do ekranu początkowego</p>
                    </div>
                  </li>
                  <li>
                    <span className="notification-installation-icon">
                      <BellIcon aria-hidden="true" size={24} />
                    </span>
                    <div>
                      <span className="text-muted text-sm">3</span>
                      <p>Otwórz Kairos → stuknij dzwonek</p>
                    </div>
                  </li>
                </ol>
                <p className="text-sm text-muted">
                  Kolejne zamówienia skanuj w aplikacji Kairos.
                </p>
              </Modal.Body>
            )}
            <Modal.Footer>
              <Button
                aria-label="Nie teraz"
                variant="secondary"
                onPress={dismissGuide}
              >
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
                  <BellIcon aria-hidden="true" size={18} />
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

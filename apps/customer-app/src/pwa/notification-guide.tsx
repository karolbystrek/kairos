"use client";

import { Button, Modal, Tooltip } from "@heroui/react";
import { X } from "lucide-react";
import { useState } from "react";

import { isAppleMobile } from "@/src/pwa/notification-onboarding";
import { useCustomerNotifications } from "@/src/pwa/notification-provider";

export function NotificationGuide() {
  const {
    canInstall,
    dismissGuide,
    enable,
    guideOpen,
    install,
    installed,
    message,
    state,
  } = useCustomerNotifications();
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const installationRequired = state === "installation-required";
  const unavailable = state === "blocked" || state === "unsupported";
  const apple = typeof navigator !== "undefined" && isAppleMobile(navigator);
  const description = installationRequired
    ? "On iPhone or iPad, open Kairos in Safari, tap Share, then Add to Home Screen and Add. Open Kairos from the Home Screen and tap the bell to enable notifications. Requires iOS or iPadOS 16.4 or later."
    : state === "blocked"
      ? "Notifications are blocked. Allow them in your browser or device settings, then tap the bell again."
      : state === "unsupported"
        ? "Notifications are unavailable in this browser or configuration. You can still track your order here."
        : "Get updates when your order is ready, completed, or canceled, even when Kairos is in the background. Allow notifications in the next browser prompt. You can turn them off with the bell at any time.";

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
              <Modal.Heading>Order notifications</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4">
              <p>{description}</p>
              {!installed && !apple && !unavailable && (
                <p className="text-muted">
                  Installation is optional.{" "}
                  {canInstall
                    ? "Install Kairos for quick access from your Home Screen or desktop."
                    : "To install, use your browser’s Install app or Add to Home Screen menu when available. In Safari on Mac, choose File → Add to Dock."}
                </p>
              )}
              <p className="text-muted text-sm">
                Tracking works without notifications. Dismissing this guide is
                remembered on this device; tap the bell to open it again.
              </p>
              {(message || actionError) && (
                <p role="status">{actionError ?? message}</p>
              )}
            </Modal.Body>
            <Modal.Footer className="flex-wrap">
              <Button variant="secondary" onPress={dismissGuide}>
                Not now
              </Button>
              {canInstall && !installed && !apple && !unavailable && (
                <Button
                  isDisabled={busy}
                  variant="secondary"
                  onPress={() => {
                    setBusy(true);
                    setActionError(null);
                    void install()
                      .catch(() => {
                        setActionError(
                          "Use your browser’s Install app menu to install Kairos.",
                        );
                      })
                      .finally(() => {
                        setBusy(false);
                      });
                  }}
                >
                  Install Kairos
                </Button>
              )}
              {!installationRequired && !unavailable && (
                <Button
                  isDisabled={busy || state === "loading"}
                  variant="primary"
                  onPress={() => {
                    setBusy(true);
                    setActionError(null);
                    void enable()
                      .catch(() => {
                        setActionError(
                          "Notifications could not be enabled. Try again or continue tracking here.",
                        );
                      })
                      .finally(() => setBusy(false));
                  }}
                >
                  Enable notifications
                </Button>
              )}
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

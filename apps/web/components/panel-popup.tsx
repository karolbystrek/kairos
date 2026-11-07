"use client";

import type { ComponentProps, ReactNode } from "react";

import { AlertDialog, Modal } from "@heroui/react";
import { XIcon } from "@phosphor-icons/react";

type PanelPopupProps = Pick<
  ComponentProps<typeof Modal>,
  "isOpen" | "onOpenChange"
> &
  Pick<
    ComponentProps<typeof Modal.Dialog>,
    "className" | "role" | "aria-label"
  > & {
    children?: ReactNode;
    isRequired?: boolean;
    // sm: confirmations, md: single-purpose forms, lg: entity details, xl: wide workflows.
    size: "sm" | "md" | "lg" | "xl";
  };

export function PanelPopup({
  children,
  className,
  isOpen,
  isRequired = false,
  onOpenChange,
  role = "dialog",
  size,
  ...props
}: PanelPopupProps) {
  return (
    <Modal
      isOpen={isOpen}
      onOpenChange={(open) => {
        if (!isRequired || open) onOpenChange?.(open);
      }}
    >
      <Modal.Backdrop
        isDismissable={!isRequired}
        isKeyboardDismissDisabled={isRequired}
      >
        <Modal.Container placement="center" size={size === "xl" ? "lg" : size}>
          <Modal.Dialog
            {...props}
            className={`panel-popup ${size === "xl" ? "panel-popup--xl" : ""} ${className ?? ""}`}
            role={role}
          >
            {!isRequired && (
              <Modal.CloseTrigger aria-label="Zamknij">
                <XIcon aria-hidden="true" size={20} />
              </Modal.CloseTrigger>
            )}
            {children}
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

PanelPopup.Header = Modal.Header;
PanelPopup.Heading = Modal.Heading;
PanelPopup.Body = Modal.Body;
PanelPopup.Footer = Modal.Footer;
PanelPopup.Icon = AlertDialog.Icon;

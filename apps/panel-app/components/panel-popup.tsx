"use client";

import type { ComponentProps, ReactNode } from "react";

import { AlertDialog, Modal } from "@heroui/react";
import { X } from "lucide-react";

type PanelPopupProps = Pick<
  ComponentProps<typeof Modal>,
  "isOpen" | "onOpenChange"
> &
  Pick<ComponentProps<typeof Modal.Container>, "size"> &
  Pick<
    ComponentProps<typeof Modal.Dialog>,
    "className" | "role" | "aria-label"
  > & {
    children?: ReactNode;
    isRequired?: boolean;
  };

export function PanelPopup({
  children,
  className,
  isOpen,
  isRequired = false,
  onOpenChange,
  role = "dialog",
  size = "lg",
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
        <Modal.Container placement="center" size={size}>
          <Modal.Dialog
            {...props}
            className={`panel-popup ${className ?? ""}`}
            role={role}
          >
            {!isRequired && (
              <Modal.CloseTrigger aria-label="Zamknij">
                <X aria-hidden="true" size={20} />
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

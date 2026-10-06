"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@heroui/react";

import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
import { changePassword } from "@/src/api/authentication";
import { ApiError } from "@/src/api/api-fetch";

export function PasswordChangeDialog({
  isOpen,
  onChanged,
  onOpenChange,
}: {
  isOpen: boolean;
  onChanged: () => Promise<void>;
  onOpenChange: (open: boolean) => void;
}) {
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  function close(value: boolean) {
    onOpenChange(value);
    setCurrent("");
    setPassword("");
    setConfirmation("");
    setError("");
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    if (password !== confirmation) {
      setError("Passwords must match.");

      return;
    }
    setPending(true);
    setError("");
    try {
      await changePassword(current, password, confirmation);
      setCurrent("");
      setPassword("");
      setConfirmation("");
      onOpenChange(false);
      await onChanged();
    } catch (failure) {
      setError(
        failure instanceof ApiError && failure.status === 400
          ? "Check your current password. The new password must contain at least 12 characters."
          : "Password could not be changed. Try again.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <PanelPopup isOpen={isOpen} onOpenChange={close}>
      <PanelPopup.Header>
        <PanelPopup.Heading>Change password</PanelPopup.Heading>
      </PanelPopup.Header>
      <form onSubmit={submit}>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            You will be signed out on every device after changing your password.
          </p>
          {error && <p role="alert">{error}</p>}
          <FormTextField
            isRequired
            inputProps={{ autoComplete: "current-password" }}
            isDisabled={pending}
            label="Current password"
            maxLength={200}
            type="password"
            value={current}
            onChange={setCurrent}
          />
          <FormTextField
            isRequired
            inputProps={{ autoComplete: "new-password" }}
            isDisabled={pending}
            label="New password"
            maxLength={200}
            type="password"
            value={password}
            onChange={setPassword}
          />
          <FormTextField
            isRequired
            inputProps={{ autoComplete: "new-password" }}
            isDisabled={pending}
            label="Confirm password"
            maxLength={200}
            type="password"
            value={confirmation}
            onChange={setConfirmation}
          />
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button variant="tertiary" onPress={() => close(false)}>
            Cancel
          </Button>
          <Button isPending={pending} type="submit">
            Change password
          </Button>
        </PanelPopup.Footer>
      </form>
    </PanelPopup>
  );
}

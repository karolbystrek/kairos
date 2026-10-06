"use client";

import { useState, type FormEvent } from "react";
import { AlertDialog, Button } from "@heroui/react";

import { FormTextField } from "@/components/form-controls";
import { changePassword } from "@/src/api/authentication";
import { ApiError } from "@/src/api/api-fetch";

export function PasswordChangeDialog({
  onChanged,
}: {
  onChanged: () => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  function close(value: boolean) {
    if (pending) return;
    setOpen(value);
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
      setOpen(false);
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
    <>
      <Button variant="tertiary" onPress={() => close(true)}>
        Change password
      </Button>
      <AlertDialog isOpen={open} onOpenChange={close}>
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog>
              <AlertDialog.Header>
                <AlertDialog.Heading>Change password</AlertDialog.Heading>
              </AlertDialog.Header>
              <form onSubmit={submit}>
                <AlertDialog.Body className="flex flex-col gap-4">
                  <p>
                    You will be signed out on every device after changing your
                    password.
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
                  <p className="text-sm text-muted">
                    Use at least 12 characters.
                  </p>
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
                </AlertDialog.Body>
                <AlertDialog.Footer>
                  <Button
                    isDisabled={pending}
                    variant="tertiary"
                    onPress={() => close(false)}
                  >
                    Cancel
                  </Button>
                  <Button isPending={pending} type="submit">
                    Change password
                  </Button>
                </AlertDialog.Footer>
              </form>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
    </>
  );
}

"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@heroui/react";
import { KeyRound } from "lucide-react";

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
      setError("Hasła muszą być takie same.");

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
          ? "Sprawdź obecne hasło. Nowe hasło musi mieć co najmniej 12 znaków."
          : "Nie udało się zmienić hasła. Spróbuj ponownie.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <PanelPopup isOpen={isOpen} onOpenChange={close}>
      <PanelPopup.Header>
        <PanelPopup.Heading>Zmień hasło</PanelPopup.Heading>
      </PanelPopup.Header>
      <form onSubmit={submit}>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>Po zmianie hasła nastąpi wylogowanie ze wszystkich urządzeń.</p>
          {error && <p role="alert">{error}</p>}
          <FormTextField
            isRequired
            inputProps={{ autoComplete: "current-password" }}
            isDisabled={pending}
            label="Obecne hasło"
            maxLength={200}
            type="password"
            value={current}
            onChange={setCurrent}
          />
          <FormTextField
            isRequired
            inputProps={{ autoComplete: "new-password" }}
            isDisabled={pending}
            label="Nowe hasło"
            maxLength={200}
            type="password"
            value={password}
            onChange={setPassword}
          />
          <FormTextField
            isRequired
            inputProps={{ autoComplete: "new-password" }}
            isDisabled={pending}
            label="Potwierdź hasło"
            maxLength={200}
            type="password"
            value={confirmation}
            onChange={setConfirmation}
          />
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button
            aria-label="Anuluj"
            variant="tertiary"
            onPress={() => close(false)}
          >
            Anuluj
          </Button>
          <Button isPending={pending} type="submit">
            <KeyRound aria-hidden="true" size={18} /> Zmień hasło
          </Button>
        </PanelPopup.Footer>
      </form>
    </PanelPopup>
  );
}

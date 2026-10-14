"use client";

import { useState, type FormEvent } from "react";
import { Button, Link } from "@heroui/react";

import { AuthFormLayout } from "@/components/auth-form-layout";
import { FormTextField } from "@/components/form-controls";
import { changePassword } from "@/src/api/authentication";
import { ApiError } from "@/src/api/api-fetch";

export function PasswordChangeForm({
  onChanged,
}: {
  onChanged: () => Promise<void>;
}) {
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

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
      await onChanged();
    } catch (failure) {
      setError(
        failure instanceof ApiError && failure.status === 400
          ? "Sprawdź obecne hasło. Nowe hasło musi mieć co najmniej 12 znaków."
          : "Nie udało się zmienić hasła. Spróbuj ponownie.",
      );
      setPending(false);
    }
  }

  return (
    <AuthFormLayout
      footer={<Link href="/dashboard">Wróć do panelu</Link>}
      title="Zmień hasło"
    >
      <form onSubmit={submit}>
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
        <Button isPending={pending} type="submit">
          Zmień hasło
        </Button>
      </form>
    </AuthFormLayout>
  );
}

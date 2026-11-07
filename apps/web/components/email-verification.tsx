"use client";

import { Button } from "@heroui/react";
import { useEffect, useState, type FormEvent } from "react";

import { AuthFormLayout } from "./auth-form-layout";
import { FormTextField } from "./form-controls";

import { ApiError } from "@/src/api/api-fetch";
import {
  logout,
  resendVerification,
  verifyEmail,
  type CurrentAccount,
  type PendingVerification,
} from "@/src/api/authentication";

export function EmailVerification({
  verification,
  onVerified,
  onChanged,
  onCancelled,
}: {
  verification: PendingVerification;
  onVerified: (account: CurrentAccount) => Promise<void>;
  onChanged: (verification: PendingVerification) => Promise<void>;
  onCancelled: () => Promise<void>;
}) {
  const [code, setCode] = useState("");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);

    return () => window.clearInterval(timer);
  }, []);
  const remaining = Math.max(
    0,
    Math.ceil((Date.parse(verification.resendAvailableAt) - now) / 1000),
  );

  async function perform(action: () => Promise<void>) {
    if (pending) return;
    setPending(true);
    setMessage("");
    try {
      await action();
    } catch (error) {
      setMessage(
        error instanceof ApiError && error.status === 429
          ? "Zbyt wiele prób. Poczekaj i spróbuj ponownie."
          : "Nie udało się potwierdzić adresu. Sprawdź kod i spróbuj ponownie. Jeśli kod wygasł, wyślij nowy.",
      );
    } finally {
      setPending(false);
    }
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!/^\d{6}$/.test(code)) {
      setMessage("Wpisz sześciocyfrowy kod z wiadomości e-mail.");

      return;
    }
    void perform(async () => onVerified(await verifyEmail(code)));
  }

  return (
    <AuthFormLayout footer={null} title="Potwierdź adres e-mail">
      <p className="mb-6 break-words">
        Wpisz kod wysłany na {verification.email}.
      </p>
      {message && (
        <p className="mb-4" role="status">
          {message}
        </p>
      )}
      <form onSubmit={submit}>
        <FormTextField
          isRequired
          inputProps={{
            autoComplete: "one-time-code",
            inputMode: "numeric",
            pattern: "[0-9]{6}",
            autoFocus: true,
          }}
          isDisabled={pending}
          label="Kod weryfikacyjny"
          maxLength={6}
          name="code"
          value={code}
          onChange={setCode}
        />
        <Button isPending={pending} type="submit">
          Potwierdź e-mail
        </Button>
      </form>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          isDisabled={pending || remaining > 0}
          variant="secondary"
          onPress={() =>
            void perform(async () => {
              await onChanged(await resendVerification());
              setCode("");
              setMessage("Nowy kod został wysłany.");
            })
          }
        >
          {remaining > 0
            ? `Wyślij ponownie za ${remaining} s`
            : "Wyślij kod ponownie"}
        </Button>
        <Button
          isDisabled={pending}
          variant="tertiary"
          onPress={() =>
            void perform(async () => {
              await logout();
              await onCancelled();
            })
          }
        >
          Zmień adres e-mail / wyloguj się
        </Button>
      </div>
    </AuthFormLayout>
  );
}

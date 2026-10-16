"use client";

import { Button, Link } from "@heroui/react";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";

import { AuthFormLayout } from "./auth-form-layout";
import { FormTextField } from "./form-controls";

import { ApiError } from "@/src/api/api-fetch";
import { requestPasswordReset, resetPassword } from "@/src/api/authentication";

const RESEND_DELAY_MS = 60_000;

function failureMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Nie udało się wykonać żądania. Sprawdź połączenie i spróbuj ponownie.";
  }
  if (error.status === 429)
    return "Zbyt wiele prób. Poczekaj i spróbuj ponownie.";
  const fields = error.problem?.fieldErrors ?? {};

  if (fields.code?.startsWith("Request a new code")) {
    return "Wpisano zbyt wiele błędnych kodów. Wyślij nowy kod.";
  }
  if (fields.code) {
    return "Kod jest nieprawidłowy lub wygasł. Sprawdź go albo wyślij nowy.";
  }
  if (fields.password) return "Hasło musi mieć od 12 do 200 znaków.";
  if (fields.email) return "Wpisz poprawny adres e-mail.";

  return error.message;
}

export function PasswordResetForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [resendAt, setResendAt] = useState(0);
  const [now, setNow] = useState(Date.now);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!codeSent) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);

    return () => window.clearInterval(timer);
  }, [codeSent]);
  const remaining = Math.max(0, Math.ceil((resendAt - now) / 1000));

  async function perform(action: () => Promise<void>) {
    if (pending) return;
    setPending(true);
    setMessage("");
    try {
      await action();
    } catch (error) {
      setMessage(failureMessage(error));
    } finally {
      setPending(false);
    }
  }

  async function sendCode() {
    await requestPasswordReset(email.trim());
    setCodeSent(true);
    setCode("");
    setNow(Date.now());
    setResendAt(Date.now() + RESEND_DELAY_MS);
  }

  function submitEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void perform(sendCode);
  }

  function submitReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!/^\d{6}$/.test(code)) {
      setMessage("Wpisz sześciocyfrowy kod z wiadomości e-mail.");

      return;
    }
    if (password !== confirmation) {
      setMessage("Hasła muszą być takie same.");

      return;
    }
    void perform(async () => {
      await resetPassword({
        email: email.trim(),
        code,
        password,
        passwordConfirmation: confirmation,
      });
      router.replace("/login?password-changed");
    });
  }

  return (
    <AuthFormLayout
      footer={<Link href="/login">Wróć do logowania</Link>}
      title="Zresetuj hasło"
    >
      {message && (
        <p className="mb-4" role="alert">
          {message}
        </p>
      )}
      {codeSent ? (
        <>
          <p className="mb-6 break-words">
            Jeśli konto o adresie {email.trim()} istnieje, wysłaliśmy na nie
            sześciocyfrowy kod. Kod jest ważny przez godzinę. Po zmianie hasła
            nastąpi wylogowanie ze wszystkich urządzeń.
          </p>
          <form onSubmit={submitReset}>
            <FormTextField
              isRequired
              inputProps={{
                autoComplete: "one-time-code",
                inputMode: "numeric",
                pattern: "[0-9]{6}",
                autoFocus: true,
              }}
              isDisabled={pending}
              label="Kod z wiadomości"
              maxLength={6}
              name="code"
              value={code}
              onChange={setCode}
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
              Ustaw nowe hasło
            </Button>
          </form>
          <div className="mt-4">
            <Button
              isDisabled={pending || remaining > 0}
              variant="secondary"
              onPress={() =>
                void perform(async () => {
                  await sendCode();
                  setMessage("Jeśli konto istnieje, wysłaliśmy nowy kod.");
                })
              }
            >
              {remaining > 0
                ? `Wyślij ponownie za ${remaining} s`
                : "Wyślij kod ponownie"}
            </Button>
          </div>
        </>
      ) : (
        <form onSubmit={submitEmail}>
          <p className="mb-6">
            Podaj adres e-mail konta. Wyślemy na niego kod do ustawienia nowego
            hasła.
          </p>
          <FormTextField
            isRequired
            inputProps={{ autoComplete: "email", autoFocus: true }}
            isDisabled={pending}
            label="E-mail"
            maxLength={200}
            type="email"
            value={email}
            onChange={setEmail}
          />
          <Button isPending={pending} type="submit">
            Wyślij kod
          </Button>
        </form>
      )}
    </AuthFormLayout>
  );
}

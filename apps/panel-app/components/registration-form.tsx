"use client";

import { LogOut, UserPlus } from "lucide-react";
import { useSyncExternalStore, useState, type FormEvent } from "react";
import { Alert, Button, Link } from "@heroui/react";
import useSWR from "swr";
import { ZodError } from "zod";

import { AuthFormLayout } from "./auth-form-layout";

import { FormTextField } from "@/components/form-controls";
import {
  registrationInputSchema,
  registerTenant,
} from "@/src/api/tenant-registrations";
import {
  previewAccountInvitation,
  redeemAccountInvitation,
} from "@/src/api/account-invitations";
import { ApiError } from "@/src/api/api-fetch";
import { getCurrentAccount, logout } from "@/src/api/authentication";

const registrationErrorMessages = new Map([
  ["Email is required.", "Podaj adres e-mail."],
  ["Enter a valid email address.", "Podaj poprawny adres e-mail."],
  ["Email must match the invitation.", "Użyj adresu e-mail z zaproszenia."],
  [
    "Email must not exceed 200 characters.",
    "Adres e-mail może mieć maksymalnie 200 znaków.",
  ],
  ["Password is required.", "Podaj hasło."],
  ["Use at least 12 characters.", "Użyj co najmniej 12 znaków."],
  [
    "Password must not exceed 200 characters.",
    "Hasło może mieć maksymalnie 200 znaków.",
  ],
  ["Confirm your password.", "Potwierdź hasło."],
  [
    "Password confirmation must not exceed 200 characters.",
    "Potwierdzenie hasła może mieć maksymalnie 200 znaków.",
  ],
  ["Passwords must match.", "Hasła muszą być takie same."],
  [
    "An account with this email already exists. Sign in instead.",
    "Konto z tym adresem e-mail już istnieje. Zaloguj się.",
  ],
  ["Password is too short.", "Hasło jest za krótkie."],
  ["Include a lowercase letter.", "Dodaj małą literę."],
  ["Include an uppercase letter.", "Dodaj wielką literę."],
  ["Include a number.", "Dodaj cyfrę."],
  ["Include a symbol.", "Dodaj znak specjalny."],
]);

export function RegistrationForm({ invited = false }: { invited?: boolean }) {
  const token = useSyncExternalStore(
    subscribeToHash,
    () =>
      invited
        ? (new URLSearchParams(window.location.hash.slice(1)).get(
            "invitation",
          ) ?? "")
        : "",
    () => undefined,
  );
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [terminalMessage, setTerminalMessage] = useState("");

  const {
    data: account,
    error: accountError,
    isLoading: checkingAccount,
    mutate: mutateAccount,
  } = useSWR(["authentication", "registration-account"], getCurrentAccount, {
    shouldRetryOnError: false,
  });

  async function signOut() {
    setPending(true);
    try {
      await logout();
      await mutateAccount(undefined, { revalidate: true });
      setMessage("");
    } catch {
      setMessage("Nie udało się wylogować. Spróbuj ponownie.");
    } finally {
      setPending(false);
    }
  }
  const { data: invitation, error: previewError } = useSWR(
    invited && token ? ["invitation-preview", token] : null,
    ([, value]) => previewAccountInvitation(value),
    {
      shouldRetryOnError: false,
      onError(error) {
        const terminal = handleInvitationProblem(error);

        if (terminal) setTerminalMessage(terminal);
      },
    },
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const input = {
      email: invited ? (invitation?.email ?? "") : email,
      password,
      passwordConfirmation: confirmation,
    };

    setFieldErrors({});
    setPending(true);
    setMessage("");
    try {
      registrationInputSchema.parse(input);
      invited
        ? await redeemAccountInvitation({ ...input, token: token ?? "" })
        : await registerTenant(input);

      setPassword("");
      setConfirmation("");
      if (invited) clearInvitationFragment();
      window.location.assign("/dashboard");
    } catch (error) {
      const terminal = invited ? handleInvitationProblem(error) : undefined;

      if (terminal) {
        setTerminalMessage(terminal);
        setPassword("");
        setConfirmation("");

        return;
      }
      if (error instanceof ZodError) {
        const errors: Record<string, string> = {};

        for (const issue of error.issues) {
          const field = String(issue.path[0]);

          errors[field] ??= issue.message;
        }
        setFieldErrors(errors);
      } else if (error instanceof ApiError && error.problem?.fieldErrors) {
        setFieldErrors(
          Object.fromEntries(
            Object.entries(error.problem.fieldErrors).map(
              ([field, message]) => [
                field,
                registrationErrorMessages.get(message) ??
                  "Sprawdź wartość tego pola.",
              ],
            ),
          ),
        );
      } else {
        setMessage(
          "Nie udało się utworzyć konta. Spróbuj ponownie po odzyskaniu połączenia.",
        );
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFormLayout
      footer={
        <>
          Masz już konto? <Link href="/login">Zaloguj się.</Link>
        </>
      }
      title={invited ? "Dołącz do zespołu" : "Utwórz konto"}
    >
      {invitation && (
        <div className="mb-6 min-w-0 space-y-1">
          <p className="text-xs font-medium text-muted">
            Zaproszenie do lokalu
          </p>
          <p className="break-words text-lg font-semibold tracking-tight">
            {invitation.locationName}
          </p>
          <p className="text-sm text-muted">
            {invitation.role === "MANAGER" ? "Kierownik" : "Pracownik"}
          </p>
        </div>
      )}
      {invited && (terminalMessage || token === "" || previewError) && (
        <Alert status="danger">
          <Alert.Content>
            {terminalMessage ||
              (previewError
                ? "Nie udało się sprawdzić zaproszenia. Spróbuj ponownie po odzyskaniu połączenia."
                : "Zaproszenie jest nieprawidłowe. Poproś zespół o nowy link.")}
          </Alert.Content>
        </Alert>
      )}
      {message && <p role="status">{message}</p>}
      {account && (
        <div className="flex flex-col gap-3">
          <p>
            Korzystasz z konta {account.email}. Wyloguj się, aby utworzyć
            kolejne konto.
          </p>
          <Button isPending={pending} onPress={() => void signOut()}>
            <LogOut aria-hidden="true" size={18} /> Wyloguj się
          </Button>
        </div>
      )}
      {accountError &&
        !(accountError instanceof ApiError && accountError.status === 401) && (
          <p role="alert">
            Nie udało się sprawdzić stanu logowania. Spróbuj ponownie.
          </p>
        )}
      {!account && (
        <form noValidate onSubmit={submit}>
          <FormTextField
            isRequired
            errorMessage={fieldErrors.email}
            inputProps={{
              autoCapitalize: "none",
              autoComplete: "email",
              autoFocus: !invited,
            }}
            isDisabled={pending}
            isReadOnly={invited}
            label="E-mail"
            maxLength={200}
            name="email"
            type="email"
            value={invited ? (invitation?.email ?? "") : email}
            onChange={(value) => {
              setEmail(value);
              setFieldErrors((current) => ({ ...current, email: "" }));
            }}
          />
          <FormTextField
            isRequired
            errorMessage={fieldErrors.password}
            inputProps={{ autoComplete: "new-password", autoFocus: invited }}
            isDisabled={pending}
            label="Hasło"
            maxLength={200}
            name="password"
            type="password"
            value={password}
            onChange={(value) => {
              setPassword(value);
              setFieldErrors((current) => ({
                ...current,
                password: "",
                passwordConfirmation: "",
              }));
            }}
          />
          <FormTextField
            isRequired
            errorMessage={fieldErrors.passwordConfirmation}
            inputProps={{ autoComplete: "new-password" }}
            isDisabled={pending}
            label="Potwierdź hasło"
            maxLength={200}
            name="passwordConfirmation"
            type="password"
            value={confirmation}
            onChange={(value) => {
              setConfirmation(value);
              setFieldErrors((current) => ({
                ...current,
                passwordConfirmation: "",
              }));
            }}
          />
          <Button
            isDisabled={
              checkingAccount ||
              (!!accountError &&
                !(
                  accountError instanceof ApiError &&
                  accountError.status === 401
                )) ||
              (invited && (!invitation || !!terminalMessage || !token))
            }
            isPending={pending}
            type="submit"
          >
            <UserPlus aria-hidden="true" size={18} /> Utwórz konto
          </Button>
        </form>
      )}
    </AuthFormLayout>
  );
}

function subscribeToHash(listener: () => void) {
  window.addEventListener("hashchange", listener);

  return () => window.removeEventListener("hashchange", listener);
}

export function clearInvitationFragment(): void {
  const url = new URL(window.location.href);
  const fragment = new URLSearchParams(url.hash.slice(1));

  if (!fragment.has("invitation")) return;
  fragment.delete("invitation");
  url.hash = fragment.toString();
  window.history.replaceState(window.history.state, "", url);
  window.dispatchEvent(new Event("hashchange"));
}

export function handleInvitationProblem(error: unknown): string | undefined {
  if (!(error instanceof ApiError) || ![404, 410].includes(error.status))
    return;

  clearInvitationFragment();
  switch (error.problem?.type) {
    case "urn:kairos:problem:account-invitation-expired":
      return "Zaproszenie wygasło. Poproś zespół o nowy link.";
    case "urn:kairos:problem:account-invitation-revoked":
      return "Zaproszenie zostało cofnięte. Poproś zespół o nowy link.";
    case "urn:kairos:problem:account-invitation-redeemed":
      return "Zaproszenie zostało już wykorzystane. Zaloguj się, jeśli masz utworzone konto.";
    default:
      return error.status === 404
        ? "Zaproszenie jest nieprawidłowe. Poproś zespół o nowy link."
        : "Zaproszenie jest niedostępne. Poproś zespół o nowy link.";
  }
}

"use client";

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
      setMessage("Sign-out could not be completed. Try again.");
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
    const input = { email, password, passwordConfirmation: confirmation };

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
      window.location.assign("/");
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
        setFieldErrors(error.problem.fieldErrors);
      } else {
        setMessage(
          "Registration could not be completed. Try again when your connection is available.",
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
          Already have an account? <Link href="/">Sign in.</Link>
        </>
      }
      title={invited ? "Join your team" : "Create account"}
    >
      {invitation && (
        <p>
          {invitation.locationName} · {invitation.role.toLowerCase()}
        </p>
      )}
      {invited && (terminalMessage || token === "" || previewError) && (
        <Alert status="danger">
          <Alert.Content>
            {terminalMessage ||
              (previewError
                ? "The invitation could not be checked. Try again when your connection is available."
                : "This invitation is invalid. Ask your team for a new link.")}
          </Alert.Content>
        </Alert>
      )}
      {message && <p role="status">{message}</p>}
      {account && (
        <div className="flex flex-col gap-3">
          <p>
            You are signed in as {account.email}. Sign out before creating
            another account.
          </p>
          <Button isPending={pending} onPress={() => void signOut()}>
            Sign out
          </Button>
        </div>
      )}
      {accountError &&
        !(accountError instanceof ApiError && accountError.status === 401) && (
          <p role="alert">
            Your sign-in state could not be checked. Try again.
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
              autoFocus: true,
            }}
            isDisabled={pending}
            label="Email"
            maxLength={200}
            name="email"
            type="email"
            value={email}
            onChange={(value) => {
              setEmail(value);
              setFieldErrors((current) => ({ ...current, email: "" }));
            }}
          />
          <FormTextField
            isRequired
            errorMessage={fieldErrors.password}
            inputProps={{ autoComplete: "new-password" }}
            isDisabled={pending}
            label="Password"
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
            label="Confirm password"
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
            Create account
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
      return "This invitation has expired. Ask your team for a new link.";
    case "urn:kairos:problem:account-invitation-revoked":
      return "This invitation was revoked. Ask your team for a new link.";
    case "urn:kairos:problem:account-invitation-redeemed":
      return "This invitation has already been used. Sign in if you created an account.";
    default:
      return error.status === 404
        ? "This invitation is invalid. Ask your team for a new link."
        : "This invitation is unavailable. Ask your team for a new link.";
  }
}

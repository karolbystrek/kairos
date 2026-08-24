"use client";

import type { FormEvent } from "react";

import { Alert, Button, Input, Label, Spinner, TextField } from "@heroui/react";
import {
  ArrowRight as ArrowRightIcon,
  LogOut as SignOutIcon,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { BrandWordmark } from "@/components/brand-wordmark";
import { ApiError } from "@/src/api/api-fetch";
import {
  getCurrentAccount,
  logout,
  type CurrentAccount,
} from "@/src/api/authentication";
import {
  previewTenantRegistrationInvitation,
  registerTenant,
  type TenantRegistrationInput,
} from "@/src/api/tenant-registrations";

const currentAccountKey = ["authentication", "current-account"] as const;

type TerminalState = "expired" | "revoked" | "redeemed" | "invalid";

function fragmentToken(): string | undefined {
  if (typeof window === "undefined") return undefined;
  const parameters = new URLSearchParams(window.location.hash.slice(1));

  return parameters.get("invitation") || undefined;
}

function removeFragment(): void {
  const cleanUrl = `${window.location.pathname}${window.location.search}`;

  window.history.replaceState(window.history.state, "", cleanUrl);
  window.dispatchEvent(new HashChangeEvent("hashchange"));
}

function subscribeToFragment(onStoreChange: () => void): () => void {
  window.addEventListener("hashchange", onStoreChange);

  return () => window.removeEventListener("hashchange", onStoreChange);
}

function terminalState(error: unknown): TerminalState | undefined {
  if (!(error instanceof ApiError)) return undefined;
  switch (error.problem?.type) {
    case "urn:kairos:problem:tenant-registration-invitation-expired":
      return "expired";
    case "urn:kairos:problem:tenant-registration-invitation-revoked":
      return "revoked";
    case "urn:kairos:problem:tenant-registration-invitation-redeemed":
      return "redeemed";
    case "urn:kairos:problem:tenant-registration-invitation-invalid":
      return "invalid";
    default:
      return error.status === 404 ? "invalid" : undefined;
  }
}

function registrationErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Check the submitted values.";
  }
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return "That username or email is already in use.";
    }
    if (error.status === 403) {
      return "Sign out before registering this tenant.";
    }

    return error.message;
  }

  return "The tenant could not be registered. Check your connection and try again.";
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function logoutMutation(): Promise<boolean> {
  return logout();
}

function registrationMutation(
  _key: readonly [string, string],
  { arg }: { arg: TenantRegistrationInput },
): Promise<CurrentAccount> {
  return registerTenant(arg);
}

export function TenantRegistration() {
  const router = useRouter();
  const token = useSyncExternalStore(
    subscribeToFragment,
    fragmentToken,
    () => undefined,
  );
  const [terminal, setTerminal] = useState<TerminalState>();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");

  const {
    data: account,
    error: accountError,
    isLoading: isAccountLoading,
    mutate: mutateAccount,
  } = useSWR(currentAccountKey, getCurrentAccount, {
    errorRetryCount: 0,
    shouldRetryOnError: false,
  });
  const {
    data: preview,
    error: previewError,
    isLoading: isPreviewLoading,
    mutate: mutatePreview,
  } = useSWR(
    token ? ["tenant-registration-invitation-preview", token] : null,
    ([, invitationToken]) =>
      previewTenantRegistrationInvitation(invitationToken),
    {
      errorRetryCount: 0,
      shouldRetryOnError: false,
      onError(error) {
        const unavailable = terminalState(error);

        if (unavailable) {
          setTerminal(unavailable);
          removeFragment();
        }
      },
    },
  );
  const {
    error: signOutError,
    isMutating: isSigningOut,
    trigger: triggerSignOut,
  } = useSWRMutation(
    ["authentication", "tenant-registration-sign-out"],
    logoutMutation,
    { throwOnError: false },
  );
  const {
    error: registrationError,
    isMutating: isRegistering,
    reset: resetRegistration,
    trigger: triggerRegistration,
  } = useSWRMutation(
    ["tenant-registration", token ?? ""] as const,
    registrationMutation,
    {
      throwOnError: false,
      onError(error) {
        const unavailable = terminalState(error);

        if (unavailable) {
          setTerminal(unavailable);
          removeFragment();
        }
      },
    },
  );

  async function signOut(): Promise<void> {
    const didSignOut = await triggerSignOut();

    if (didSignOut) await mutateAccount(undefined, { revalidate: false });
  }

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!token || isRegistering) return;
    resetRegistration();
    const registered = await triggerRegistration({
      token,
      username,
      email,
      password,
      passwordConfirmation,
    });

    if (!registered) return;
    removeFragment();
    await mutateAccount(registered, { revalidate: false });
    router.replace("/");
  }

  if (terminal) {
    const messages = {
      expired: [
        "This invitation has expired",
        "Ask a Kairos platform operator for a new invitation.",
      ],
      revoked: [
        "This invitation was revoked",
        "Ask a Kairos platform operator for a new invitation.",
      ],
      redeemed: [
        "This invitation was already used",
        "Sign in with the administrator account created from it.",
      ],
      invalid: [
        "This invitation is not valid",
        "Check that you opened the complete invitation link.",
      ],
    } as const;

    return (
      <InvitationState
        description={messages[terminal][1]}
        title={messages[terminal][0]}
      />
    );
  }

  if (!token) {
    return (
      <InvitationState
        description="Open the complete link sent by a Kairos platform operator."
        title="A tenant invitation is required"
      />
    );
  }

  const accountCheckFailed =
    accountError &&
    !(accountError instanceof ApiError && accountError.status === 401);

  if (previewError || accountCheckFailed) {
    return (
      <InvitationState
        action={
          <Button
            onPress={() => {
              void mutateAccount();
              void mutatePreview();
            }}
          >
            Retry
          </Button>
        }
        description="Check your connection and try again."
        title="Invitation could not load"
      />
    );
  }

  if (isPreviewLoading || isAccountLoading || !preview) {
    return (
      <div className="flex min-h-[70vh] flex-col items-center justify-center">
        <BrandWordmark className="mb-8" />
        <Spinner aria-label="Loading tenant invitation" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-[calc(100vh-5rem)] max-w-md items-center py-8">
      <div className="w-full">
        <BrandWordmark className="mb-10" />
        <h1 className="text-2xl font-semibold tracking-tight">
          Register your restaurant
        </h1>
        <dl className="mt-5 border-y border-separator py-4">
          <div>
            <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
              Invitation expires
            </dt>
            <dd className="mt-1 font-medium">
              {formatDateTime(preview.expiresAt)}
            </dd>
          </div>
        </dl>

        {account ? (
          <div className="mt-6 flex flex-col gap-4">
            <Alert status="warning">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>Sign out to continue</Alert.Title>
                <Alert.Description>
                  You are signed in as {account.username}. The invitation will
                  remain available after signing out.
                </Alert.Description>
              </Alert.Content>
            </Alert>
            {signOutError && (
              <Alert status="danger">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title>Sign-out failed</Alert.Title>
                  <Alert.Description>
                    Try again before registering this tenant.
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}
            <Button
              isPending={isSigningOut}
              variant="danger"
              onPress={() => void signOut()}
            >
              <SignOutIcon size={18} />
              Sign out
            </Button>
          </div>
        ) : (
          <form className="mt-6 flex flex-col gap-4" onSubmit={submit}>
            {registrationError && !terminalState(registrationError) && (
              <Alert status="danger">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title>Tenant could not be registered</Alert.Title>
                  <Alert.Description>
                    {registrationErrorMessage(registrationError)}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}
            <TextField
              fullWidth
              isRequired
              isDisabled={isRegistering}
              maxLength={120}
              name="username"
              value={username}
              onChange={setUsername}
            >
              <Label>Administrator username</Label>
              <Input
                autoCapitalize="none"
                autoComplete="username"
                spellCheck={false}
              />
            </TextField>
            <TextField
              fullWidth
              isRequired
              isDisabled={isRegistering}
              maxLength={254}
              name="email"
              type="email"
              value={email}
              onChange={setEmail}
            >
              <Label>Administrator email</Label>
              <Input
                autoCapitalize="none"
                autoComplete="email"
                spellCheck={false}
              />
            </TextField>
            <TextField
              fullWidth
              isRequired
              isDisabled={isRegistering}
              name="password"
              type="password"
              value={password}
              onChange={setPassword}
            >
              <Label>Password</Label>
              <Input autoComplete="new-password" />
            </TextField>
            <TextField
              fullWidth
              isRequired
              isDisabled={isRegistering}
              name="passwordConfirmation"
              type="password"
              value={passwordConfirmation}
              onChange={setPasswordConfirmation}
            >
              <Label>Confirm password</Label>
              <Input autoComplete="new-password" />
            </TextField>
            <Button fullWidth isPending={isRegistering} type="submit">
              {isRegistering ? "Registering tenant…" : "Register tenant"}
              {!isRegistering && <ArrowRightIcon size={18} />}
            </Button>
          </form>
        )}
      </div>
    </div>
  );
}

function InvitationState({
  action,
  description,
  title,
}: {
  action?: React.ReactNode;
  description?: string;
  title: string;
}) {
  return (
    <div className="flex min-h-[70vh] items-center justify-center text-center">
      <div>
        <BrandWordmark className="mb-8" />
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="mt-2 secondary-text">{description}</p>}
        {action && <div className="mt-5 flex justify-center">{action}</div>}
      </div>
    </div>
  );
}

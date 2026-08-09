"use client";

import type { FormEvent } from "react";

import { Alert, Button, Input, Label, Spinner, TextField } from "@heroui/react";
import {
  ArrowRight as ArrowRightIcon,
  LogOut as SignOutIcon,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState, useSyncExternalStore } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { BrandWordmark } from "@/components/brand-wordmark";
import {
  previewAccountInvitation,
  redeemAccountInvitation,
  type RedeemAccountInvitationInput,
} from "@/src/api/account-invitations";
import { ApiError } from "@/src/api/api-fetch";
import {
  getCurrentAccount,
  logout,
  type CurrentAccount,
} from "@/src/api/authentication";

const currentAccountKey = ["authentication", "current-account"] as const;

function fragmentToken(): string | undefined {
  if (typeof window === "undefined") return undefined;
  const parameters = new URLSearchParams(window.location.hash.slice(1));

  return parameters.get("invitation") || undefined;
}

function removeFragment(): void {
  const cleanUrl = `${window.location.pathname}${window.location.search}`;

  window.history.replaceState(window.history.state, "", cleanUrl);
}

function subscribeToFragment(onStoreChange: () => void): () => void {
  window.addEventListener("hashchange", onStoreChange);

  return () => window.removeEventListener("hashchange", onStoreChange);
}

function terminalState(
  error: unknown,
): "expired" | "revoked" | "redeemed" | "invalid" | undefined {
  if (!(error instanceof ApiError)) return undefined;
  switch (error.problem?.type) {
    case "urn:kairos:problem:account-invitation-expired":
      return "expired";
    case "urn:kairos:problem:account-invitation-revoked":
      return "revoked";
    case "urn:kairos:problem:account-invitation-redeemed":
      return "redeemed";
    case "urn:kairos:problem:account-invitation-invalid":
      return "invalid";
    default:
      return error.status === 404 ? "invalid" : undefined;
  }
}

function redemptionErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Check the submitted values.";
  }
  if (error instanceof ApiError) {
    if (error.status === 409)
      return "That username or email is already in use.";
    if (error.status === 403)
      return "Sign out before accepting this invitation.";

    return error.message;
  }

  return "The account could not be created. Check your connection and try again.";
}

function logoutMutation(): Promise<boolean> {
  return logout();
}

function redemptionMutation(
  _key: readonly [string, string],
  { arg }: { arg: RedeemAccountInvitationInput },
): Promise<CurrentAccount> {
  return redeemAccountInvitation(arg);
}

export function AccountRegistration() {
  const router = useRouter();
  const token = useSyncExternalStore(
    subscribeToFragment,
    fragmentToken,
    () => undefined,
  );
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [localError, setLocalError] = useState<string>();

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
  } = useSWR(
    token ? ["account-invitation-preview", token] : null,
    ([, invitationToken]) => previewAccountInvitation(invitationToken),
    { errorRetryCount: 0, shouldRetryOnError: false },
  );
  const {
    error: signOutError,
    isMutating: isSigningOut,
    trigger: triggerSignOut,
  } = useSWRMutation(
    ["authentication", "invitation-sign-out"],
    logoutMutation,
    {
      throwOnError: false,
    },
  );
  const {
    error: redemptionError,
    isMutating: isRedeeming,
    reset: resetRedemption,
    trigger: triggerRedemption,
  } = useSWRMutation(
    ["account-invitation-redemption", token ?? ""] as const,
    redemptionMutation,
    {
      throwOnError: false,
      onError(error) {
        if (terminalState(error)) removeFragment();
      },
    },
  );

  const unusableState =
    terminalState(previewError) ?? terminalState(redemptionError);

  useEffect(() => {
    if (unusableState) removeFragment();
  }, [unusableState]);

  async function signOut() {
    const didSignOut = await triggerSignOut();

    if (didSignOut) await mutateAccount(undefined, { revalidate: false });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || isRedeeming) return;
    setLocalError(undefined);
    resetRedemption();
    if (password !== passwordConfirmation) {
      setLocalError("Passwords must match.");

      return;
    }
    const registered = await triggerRedemption({
      token,
      username,
      email,
      password,
    });

    if (!registered) return;
    removeFragment();
    await mutateAccount(registered, { revalidate: false });
    router.replace("/");
  }

  if (!token && !unusableState) {
    return <InvitationState title="This invitation is not valid" />;
  }

  if (unusableState) {
    const messages = {
      expired: [
        "This invitation has expired",
        "Ask the restaurant for a new invitation.",
      ],
      revoked: [
        "This invitation was revoked",
        "Ask the restaurant for a new invitation.",
      ],
      redeemed: [
        "This invitation was already used",
        "Sign in with the account created from it.",
      ],
      invalid: [
        "This invitation is not valid",
        "Check that you opened the complete link.",
      ],
    } as const;

    return (
      <InvitationState
        description={messages[unusableState][1]}
        title={messages[unusableState][0]}
      />
    );
  }

  if (isPreviewLoading || isAccountLoading || !preview) {
    if (previewError && !unusableState) {
      return (
        <InvitationState
          description="Check your connection and try opening the link again."
          title="Invitation could not load"
        />
      );
    }

    return (
      <div className="flex min-h-[70vh] flex-col items-center justify-center">
        <BrandWordmark className="mb-8" />
        <Spinner aria-label="Loading invitation" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-[calc(100vh-5rem)] max-w-md items-center py-8">
      <div className="w-full">
        <BrandWordmark className="mb-10" />
        <h1 className="text-2xl font-semibold tracking-tight">
          Create your account
        </h1>
        <dl className="mt-5 grid grid-cols-2 gap-4 border-y border-separator py-4">
          <div>
            <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
              Location
            </dt>
            <dd className="mt-1 font-medium">{preview.locationName}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
              Role
            </dt>
            <dd className="mt-1 font-medium">
              {preview.role === "MANAGER" ? "Manager" : "Operator"}
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
                    Try again before accepting the invitation.
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
            {(localError || redemptionError) && (
              <Alert status="danger">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title>Account could not be created</Alert.Title>
                  <Alert.Description>
                    {localError ?? redemptionErrorMessage(redemptionError)}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}
            <TextField
              fullWidth
              isRequired
              isDisabled={isRedeeming}
              maxLength={120}
              name="username"
              value={username}
              onChange={setUsername}
            >
              <Label>Username</Label>
              <Input
                autoCapitalize="none"
                autoComplete="username"
                spellCheck={false}
              />
            </TextField>
            <TextField
              fullWidth
              isRequired
              isDisabled={isRedeeming}
              maxLength={254}
              name="email"
              type="email"
              value={email}
              onChange={setEmail}
            >
              <Label>Email</Label>
              <Input
                autoCapitalize="none"
                autoComplete="email"
                spellCheck={false}
              />
            </TextField>
            <TextField
              fullWidth
              isRequired
              isDisabled={isRedeeming}
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
              isDisabled={isRedeeming}
              name="passwordConfirmation"
              type="password"
              value={passwordConfirmation}
              onChange={setPasswordConfirmation}
            >
              <Label>Confirm password</Label>
              <Input autoComplete="new-password" />
            </TextField>
            <Button fullWidth isPending={isRedeeming} type="submit">
              {isRedeeming ? "Creating account…" : "Create account"}
              {!isRedeeming && <ArrowRightIcon size={18} />}
            </Button>
          </form>
        )}
        {accountError instanceof ApiError && accountError.status !== 401 && (
          <p className="mt-4 text-sm text-danger">
            Your sign-in state could not be checked. Reload before continuing.
          </p>
        )}
      </div>
    </div>
  );
}

function InvitationState({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <div className="flex min-h-[70vh] items-center justify-center text-center">
      <div>
        <BrandWordmark className="mb-8" />
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="mt-2 secondary-text">{description}</p>}
      </div>
    </div>
  );
}

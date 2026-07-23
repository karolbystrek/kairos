"use client";

import type { FormEvent } from "react";

import {
  Alert,
  Button,
  Input,
  Label,
  Spinner,
  Surface,
  TextField,
} from "@heroui/react";
import { useEffect, useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { OrderManagement } from "@/components/order-management";
import { subscribeToAuthenticationRequired } from "@/src/api/auth-coordination";
import { ApiError } from "@/src/api/api-fetch";
import {
  getCurrentAccount,
  login as loginRequest,
  logout as logoutRequest,
  type CurrentAccount,
  type LoginCredentials,
} from "@/src/api/authentication";
import { isStaffCacheKey } from "@/src/api/cache-keys";

const currentAccountKey = ["authentication", "current-account"] as const;
const logoutKey = ["authentication", "logout"] as const;

function loginMutation(
  _key: typeof currentAccountKey,
  { arg }: { arg: LoginCredentials },
): Promise<CurrentAccount> {
  return loginRequest(arg);
}

function logoutMutation(): Promise<boolean> {
  return logoutRequest();
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "The submitted values are not valid.";
  }

  return error instanceof Error
    ? error.message
    : "An unexpected error occurred.";
}

function LoginForm({
  error,
  isPending,
  onSubmit,
}: {
  error?: Error;
  isPending: boolean;
  onSubmit: (credentials: LoginCredentials) => Promise<void>;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;

    await onSubmit({ username, password });
    setPassword("");
  }

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <Surface className="flex w-full max-w-md flex-col gap-6 rounded-3xl p-6 sm:p-8">
        <div className="flex flex-col gap-2">
          <h1 className="text-3xl font-semibold">Kairos Staff Panel</h1>
          <p className="text-muted">
            Sign in with the account provisioned for this panel.
          </p>
        </div>

        {error && (
          <Alert status="danger">
            <Alert.Indicator />
            <Alert.Content>
              <Alert.Title>Sign-in failed</Alert.Title>
              <Alert.Description>{getErrorMessage(error)}</Alert.Description>
            </Alert.Content>
          </Alert>
        )}

        <form className="flex flex-col gap-4" onSubmit={submit}>
          <TextField
            fullWidth
            isRequired
            isDisabled={isPending}
            maxLength={120}
            name="username"
            value={username}
            onChange={setUsername}
          >
            <Label>Username</Label>
            <Input
              autoCapitalize="none"
              autoComplete="username"
              placeholder="panel.username"
              spellCheck={false}
            />
          </TextField>

          <TextField
            fullWidth
            isRequired
            isDisabled={isPending}
            maxLength={256}
            name="password"
            type="password"
            value={password}
            onChange={setPassword}
          >
            <Label>Password</Label>
            <Input autoComplete="current-password" />
          </TextField>

          <Button fullWidth isPending={isPending} type="submit">
            {isPending ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </Surface>
    </div>
  );
}

function accountScope(account: CurrentAccount): string {
  if (account.tenantRole === "ADMIN") return "Tenant administrator";

  const role = account.assignment?.role === "MANAGER" ? "Manager" : "Operator";

  return account.assignment
    ? `${account.assignment.locationName} · ${role}`
    : role;
}

export function StaffPanel() {
  const { mutate: mutateCache } = useSWRConfig();
  const {
    data: account,
    error: accountError,
    isLoading,
    isValidating,
    mutate: mutateAccount,
  } = useSWR(currentAccountKey, getCurrentAccount, {
    errorRetryCount: 3,
    shouldRetryOnError,
  });

  const {
    error: loginError,
    isMutating: isLoggingIn,
    reset: resetLogin,
    trigger: triggerLogin,
  } = useSWRMutation(currentAccountKey, loginMutation, {
    throwOnError: false,
  });

  const {
    error: logoutError,
    isMutating: isLoggingOut,
    trigger: triggerLogout,
  } = useSWRMutation(logoutKey, logoutMutation, {
    throwOnError: false,
  });

  useEffect(
    () =>
      subscribeToAuthenticationRequired(() => {
        void mutateCache(isStaffCacheKey, undefined, { revalidate: false });
        void mutateAccount(undefined, { revalidate: false });
      }),
    [mutateAccount, mutateCache],
  );

  async function signIn(credentials: LoginCredentials) {
    resetLogin();
    const currentAccount = await triggerLogin(credentials);

    if (!currentAccount) return;

    await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
    await mutateAccount(currentAccount, { revalidate: false });
  }

  async function signOut() {
    const didLogout = await triggerLogout();

    if (!didLogout) return;

    await mutateAccount(undefined, { revalidate: false });
    await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
  }

  const isUnauthorized =
    accountError instanceof ApiError && accountError.status === 401;
  const isSignedOut = !account && !accountError && !isLoading;

  if (isUnauthorized || isSignedOut) {
    return (
      <LoginForm error={loginError} isPending={isLoggingIn} onSubmit={signIn} />
    );
  }

  if (isLoading && !account) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner aria-label="Checking authentication" />
      </div>
    );
  }

  if (!account) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Alert className="max-w-xl" status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Authentication unavailable</Alert.Title>
            <Alert.Description>
              {getErrorMessage(accountError)}
            </Alert.Description>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="danger"
                onPress={() => void mutateAccount()}
              >
                Try again
              </Button>
            </div>
          </Alert.Content>
        </Alert>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Kairos Staff Panel</h1>
          <p className="text-muted">
            {account.displayName} · {accountScope(account)}
          </p>
          {isValidating && (
            <p className="text-sm text-muted">Refreshing account…</p>
          )}
        </div>
        <Button isPending={isLoggingOut} variant="secondary" onPress={signOut}>
          {isLoggingOut ? "Signing out…" : "Sign out"}
        </Button>
      </header>

      {accountError && (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Account refresh failed</Alert.Title>
            <Alert.Description>
              {getErrorMessage(accountError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {logoutError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Sign-out failed</Alert.Title>
            <Alert.Description>
              {getErrorMessage(logoutError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <OrderManagement key={account.accountId} accountId={account.accountId} />
    </div>
  );
}

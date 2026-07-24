"use client";

import type { FormEvent } from "react";
import type { TenantRegistration } from "@/src/api/tenant-registrations";

import {
  Alert,
  Button,
  Input,
  Label,
  Spinner,
  Surface,
  Tabs,
  TextField,
} from "@heroui/react";
import { useEffect, useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { OrderManagement } from "@/components/order-management";
import { AccountManagement } from "@/components/account-management";
import { TenantRegistrationForm } from "@/components/tenant-registration-form";
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
  confirmation,
  error,
  initialUsername,
  isPending,
  onSubmit,
}: {
  confirmation?: string;
  error?: Error;
  initialUsername?: string;
  isPending: boolean;
  onSubmit: (credentials: LoginCredentials) => Promise<void>;
}) {
  const [username, setUsername] = useState(initialUsername ?? "");
  const [password, setPassword] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;

    await onSubmit({ username, password });
    setPassword("");
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="text-xl font-semibold">Sign in</h2>
        <p className="text-sm text-muted">
          Use an account registered for this panel.
        </p>
      </div>

      {confirmation && (
        <Alert status="success">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Tenant registered</Alert.Title>
            <Alert.Description>{confirmation}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

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
    </div>
  );
}

function SignedOutPanel({
  loginError,
  isLoggingIn,
  onSignIn,
}: {
  loginError?: Error;
  isLoggingIn: boolean;
  onSignIn: (credentials: LoginCredentials) => Promise<void>;
}) {
  const [selectedView, setSelectedView] = useState<"login" | "register">(
    "login",
  );
  const [registration, setRegistration] = useState<TenantRegistration>();

  function registered(result: TenantRegistration) {
    setRegistration(result);
    setSelectedView("login");
  }

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <Surface className="flex w-full max-w-xl flex-col gap-6 rounded-3xl p-6 sm:p-8">
        <div className="flex flex-col gap-2">
          <h1 className="text-3xl font-semibold">Kairos Staff Panel</h1>
          <p className="text-muted">
            Sign in to an existing account or register a new tenant.
          </p>
        </div>

        <Tabs
          selectedKey={selectedView}
          onSelectionChange={(key) =>
            setSelectedView(key === "register" ? "register" : "login")
          }
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="Authentication">
              <Tabs.Tab id="login">
                Sign in
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="register">
                Register tenant
                <Tabs.Indicator />
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>
          <Tabs.Panel className="pt-5" id="login">
            <LoginForm
              key={registration?.username ?? "login"}
              confirmation={
                registration
                  ? "Sign in with the administrator account below."
                  : undefined
              }
              error={loginError}
              initialUsername={registration?.username}
              isPending={isLoggingIn}
              onSubmit={onSignIn}
            />
          </Tabs.Panel>
          <Tabs.Panel className="pt-5" id="register">
            <TenantRegistrationForm onRegistered={registered} />
          </Tabs.Panel>
        </Tabs>
      </Surface>
    </div>
  );
}

function accountScope(account: CurrentAccount): string {
  if (account.tenantRole === "ADMIN") return "Tenant administrator";

  const role = account.assignment?.role === "MANAGER" ? "Manager" : "Operator";

  return role;
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
      <SignedOutPanel
        isLoggingIn={isLoggingIn}
        loginError={loginError}
        onSignIn={signIn}
      />
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
            {account.username} · {accountScope(account)}
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

      {account.capabilities.includes("PROVISION_OPERATORS") ? (
        <Tabs>
          <Tabs.ListContainer>
            <Tabs.List aria-label="Staff workspace">
              <Tabs.Tab id="orders">
                Orders
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="accounts">
                Accounts
                <Tabs.Indicator />
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>
          <Tabs.Panel className="pt-6" id="orders">
            <OrderManagement
              key={`orders-${account.accountId}`}
              accountId={account.accountId}
            />
          </Tabs.Panel>
          <Tabs.Panel className="pt-6" id="accounts">
            <AccountManagement
              key={`accounts-${account.accountId}`}
              account={account}
            />
          </Tabs.Panel>
        </Tabs>
      ) : (
        <OrderManagement
          key={account.accountId}
          accountId={account.accountId}
        />
      )}
    </div>
  );
}

"use client";

import type { FormEvent } from "react";

import {
  Alert,
  AlertDialog,
  Button,
  Spinner,
  Tabs,
  Tooltip,
} from "@heroui/react";
import {
  ArrowRight as ArrowRightIcon,
  ClipboardList as OrdersIcon,
  LogOut as SignOutIcon,
  MapPin as LocationsIcon,
  Users as TeamIcon,
  Workflow as IntegrationIcon,
  X as CloseIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { FormTextField } from "@/components/form-controls";
import { OrderManagement } from "@/components/order-management";
import { AccountManagement } from "@/components/account-management";
import { BrandWordmark } from "@/components/brand-wordmark";
import { IntegrationManagement } from "@/components/integration-management";
import { LocationManagement } from "@/components/location-management";
import { PasswordChangeDialog } from "@/components/password-change-dialog";
import { PanelAppearanceMenu } from "@/components/panel-appearance-menu";
import { subscribeToAuthenticationRequired } from "@/src/api/auth-state";
import { ApiError } from "@/src/api/api-fetch";
import {
  getCurrentAccount,
  login as loginRequest,
  logout as logoutRequest,
  type AuthenticationResult,
  type LoginCredentials,
} from "@/src/api/authentication";
import { isStaffCacheKey } from "@/src/api/cache-keys";

const currentAccountKey = ["authentication", "current-account"] as const;
const logoutKey = ["authentication", "logout"] as const;

function DismissibleNotice({
  description,
  onDismiss,
  status,
  title,
}: {
  description: string;
  onDismiss: () => void;
  status: "danger" | "success" | "warning";
  title: string;
}) {
  return (
    <Alert status={status}>
      <Alert.Indicator />
      <Alert.Content>
        <Alert.Title>{title}</Alert.Title>
        <Alert.Description>{description}</Alert.Description>
      </Alert.Content>
      <Tooltip delay={500}>
        <Tooltip.Trigger>
          <Button
            isIconOnly
            aria-label="Dismiss notification"
            className="notice-dismiss rounded-md"
            variant="tertiary"
            onPress={onDismiss}
          >
            <CloseIcon size={18} />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content>Dismiss</Tooltip.Content>
      </Tooltip>
    </Alert>
  );
}

function loginMutation(
  _key: typeof currentAccountKey,
  { arg }: { arg: LoginCredentials },
): Promise<AuthenticationResult> {
  return loginRequest(arg);
}

function logoutMutation(
  _key: typeof logoutKey,
  { arg }: { arg: boolean },
): Promise<boolean> {
  return logoutRequest(arg);
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

  if (error instanceof ApiError) return error.message;

  return "The request could not be completed. Check your connection and try again.";
}

function getLoginErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return "The email or password is incorrect.";
  }

  return getErrorMessage(error);
}

function LoginForm({
  error,
  isPending,
  onDismissError,
  onSubmit,
}: {
  error?: Error;
  isPending: boolean;
  onDismissError: () => void;
  onSubmit: (credentials: LoginCredentials) => Promise<void>;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;

    await onSubmit({ email, password });
    setPassword("");
  }

  return (
    <div className="flex flex-col gap-5">
      {error && (
        <DismissibleNotice
          description={getLoginErrorMessage(error)}
          status="danger"
          title="Sign-in failed"
          onDismiss={onDismissError}
        />
      )}

      <form className="flex flex-col gap-4" onSubmit={submit}>
        <FormTextField
          fullWidth
          isRequired
          inputProps={{
            autoCapitalize: "none",
            autoComplete: "email",
            spellCheck: false,
          }}
          isDisabled={isPending}
          label="Email"
          maxLength={200}
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
        />

        <FormTextField
          fullWidth
          isRequired
          inputProps={{ autoComplete: "current-password" }}
          isDisabled={isPending}
          label="Password"
          maxLength={200}
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
        />

        <Button fullWidth isPending={isPending} type="submit">
          {isPending ? "Signing in…" : "Sign in"}
          {!isPending && <ArrowRightIcon size={18} />}
        </Button>
      </form>
      <Button
        variant="secondary"
        onPress={() => {
          window.location.assign("/tenant-registration");
        }}
      >
        Create account
      </Button>
    </div>
  );
}

function SignedOutPanel({
  loginError,
  isLoggingIn,
  onDismissLoginError,
  onSignIn,
}: {
  loginError?: Error;
  isLoggingIn: boolean;
  onDismissLoginError: () => void;
  onSignIn: (credentials: LoginCredentials) => Promise<void>;
}) {
  return (
    <div className="flex min-h-[calc(100vh-5rem)] items-center justify-center py-8">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <BrandWordmark />
        </div>

        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold tracking-tight">
              Welcome back
            </h1>
          </div>
          <LoginForm
            error={loginError}
            isPending={isLoggingIn}
            onDismissError={onDismissLoginError}
            onSubmit={onSignIn}
          />
        </div>
      </div>
    </div>
  );
}

export function StaffPanel() {
  const { mutate: mutateCache } = useSWRConfig();
  const [selectedWorkspace, setSelectedWorkspace] = useState("orders");
  const [requestedOrderLocationId, setRequestedOrderLocationId] =
    useState<string>();
  const [isSignOutConfirmationOpen, setIsSignOutConfirmationOpen] =
    useState(false);
  const {
    data: account,
    error: accountError,
    isLoading,
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
    reset: resetLogout,
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

  async function signOut(everywhere = false) {
    const didLogout = await triggerLogout(everywhere);

    if (!didLogout) return;

    setIsSignOutConfirmationOpen(false);
    await mutateAccount(undefined, { revalidate: false });
    await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
  }

  const isUnauthorized =
    accountError instanceof ApiError && accountError.status === 401;
  const isSignedOut = !account && !accountError && !isLoading;

  if (isUnauthorized || isSignedOut) {
    return (
      <>
        <SignedOutPanel
          isLoggingIn={isLoggingIn}
          loginError={loginError}
          onDismissLoginError={resetLogin}
          onSignIn={signIn}
        />
      </>
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

  const canManageAccounts = account.capabilities.includes("INVITE_OPERATORS");
  const canManageLocations = account.capabilities.includes("MANAGE_LOCATIONS");
  const canManageIntegrations = account.capabilities.includes(
    "MANAGE_EXTERNAL_INTEGRATIONS",
  );
  const utilities = (
    <div className="panel-workspace-utilities flex shrink-0 items-center gap-2">
      <PasswordChangeDialog
        onChanged={async () => {
          await mutateAccount(undefined, { revalidate: false });
          await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
        }}
      />
      <PanelAppearanceMenu />
      <Tooltip delay={500}>
        <Tooltip.Trigger>
          <Button
            isIconOnly
            aria-label={isLoggingOut ? "Signing out" : "Sign out"}
            className="rounded-md"
            isPending={isLoggingOut}
            size="lg"
            variant="tertiary"
            onPress={() => {
              resetLogout();
              setIsSignOutConfirmationOpen(true);
            }}
          >
            <SignOutIcon size={20} />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content>Sign out</Tooltip.Content>
      </Tooltip>
    </div>
  );

  return (
    <div className="flex flex-col gap-6">
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

      {canManageAccounts || canManageLocations || canManageIntegrations ? (
        <Tabs
          selectedKey={selectedWorkspace}
          onSelectionChange={(key) => setSelectedWorkspace(String(key))}
        >
          <div className="panel-workspace-controls">
            <div aria-hidden="true" className="panel-workspace-spacer" />
            <Tabs.ListContainer className="mobile-navigation panel-navigation">
              <Tabs.List
                aria-label="Staff workspace"
                className="w-full justify-around sm:w-auto"
              >
                <Tabs.Tab id="orders">
                  <span className="tab-motion-content">
                    <OrdersIcon size={18} />
                    <span>Orders</span>
                  </span>
                  <Tabs.Indicator />
                </Tabs.Tab>
                {canManageLocations && (
                  <Tabs.Tab id="locations">
                    <span className="tab-motion-content">
                      <LocationsIcon size={18} />
                      <span>Locations</span>
                    </span>
                    <Tabs.Indicator />
                  </Tabs.Tab>
                )}
                {canManageAccounts && (
                  <Tabs.Tab id="accounts">
                    <span className="tab-motion-content">
                      <TeamIcon size={18} />
                      <span>Accounts</span>
                    </span>
                    <Tabs.Indicator />
                  </Tabs.Tab>
                )}
                {canManageIntegrations && (
                  <Tabs.Tab id="integrations">
                    <span className="tab-motion-content">
                      <IntegrationIcon size={18} />
                      <span>Integrations</span>
                    </span>
                    <Tabs.Indicator />
                  </Tabs.Tab>
                )}
              </Tabs.List>
            </Tabs.ListContainer>
            {utilities}
          </div>
          <Tabs.Panel className="pt-6" id="orders">
            <OrderManagement
              key={`orders-${account.accountId}`}
              accountId={account.accountId}
              canManageLocations={canManageLocations}
              canViewTenantOrders={account.capabilities.includes(
                "VIEW_TENANT_ORDERS",
              )}
              requestedLocationId={requestedOrderLocationId}
              onRequestedLocationApplied={() =>
                setRequestedOrderLocationId(undefined)
              }
            />
          </Tabs.Panel>
          {canManageLocations && (
            <Tabs.Panel className="pt-6" id="locations">
              <LocationManagement
                key={`locations-${account.accountId}`}
                accountId={account.accountId}
                onViewOrders={(locationId) => {
                  setRequestedOrderLocationId(locationId);
                  setSelectedWorkspace("orders");
                }}
              />
            </Tabs.Panel>
          )}
          {canManageAccounts && (
            <Tabs.Panel className="pt-6" id="accounts">
              <AccountManagement
                key={`accounts-${account.accountId}`}
                account={account}
              />
            </Tabs.Panel>
          )}
          {canManageIntegrations && (
            <Tabs.Panel className="pt-6" id="integrations">
              <IntegrationManagement
                key={`integrations-${account.accountId}`}
                accountId={account.accountId}
              />
            </Tabs.Panel>
          )}
        </Tabs>
      ) : (
        <>
          <div className="flex justify-end">{utilities}</div>
          <OrderManagement
            key={account.accountId}
            accountId={account.accountId}
            canManageLocations={false}
            canViewTenantOrders={account.capabilities.includes(
              "VIEW_TENANT_ORDERS",
            )}
          />
        </>
      )}

      <AlertDialog
        isOpen={isSignOutConfirmationOpen}
        onOpenChange={(open) => {
          if (isLoggingOut) return;

          setIsSignOutConfirmationOpen(open);
          if (!open) resetLogout();
        }}
      >
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[420px]">
              <AlertDialog.CloseTrigger />
              <AlertDialog.Header>
                <AlertDialog.Icon status="warning" />
                <AlertDialog.Heading>Sign out?</AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body className="flex flex-col gap-4">
                <p>You will need to sign in again to manage orders.</p>
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
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button
                  isDisabled={isLoggingOut}
                  variant="secondary"
                  onPress={() => void signOut(true)}
                >
                  Sign out everywhere
                </Button>
                <Button
                  isDisabled={isLoggingOut}
                  slot="close"
                  variant="tertiary"
                >
                  Cancel
                </Button>
                <Button
                  isPending={isLoggingOut}
                  variant="danger"
                  onPress={() => void signOut()}
                >
                  Sign out
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
    </div>
  );
}

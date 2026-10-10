"use client";

import type { FormEvent } from "react";

import {
  Alert,
  Button,
  Dropdown,
  Link,
  Spinner,
  Tabs,
  Tooltip,
} from "@heroui/react";
import {
  ClipboardList as OrdersIcon,
  UserRound as AccountIcon,
  MapPin as LocationsIcon,
  Users as TeamIcon,
  Workflow as IntegrationIcon,
  X as CloseIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import useSWR, { useSWRConfig } from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
import { OrderManagement } from "@/components/order-management";
import { AccountManagement } from "@/components/account-management";
import { AuthFormLayout } from "@/components/auth-form-layout";
import { IntegrationManagement } from "@/components/integration-management";
import { LocationCreationModal } from "@/components/location-creation-modal";
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
import { listLocations } from "@/src/api/locations";
import {
  currentAccountKey,
  isStaffCacheKey,
  staffLocationsKey,
} from "@/src/api/cache-keys";

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
            aria-label="Zamknij powiadomienie"
            className="notice-dismiss rounded-md"
            variant="tertiary"
            onPress={onDismiss}
          >
            <CloseIcon size={18} />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content>Zamknij</Tooltip.Content>
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
    return error.issues[0]?.message ?? "Wprowadzone dane są nieprawidłowe.";
  }

  if (error instanceof ApiError) return error.message;

  return "Nie udało się wykonać operacji. Sprawdź połączenie i spróbuj ponownie.";
}

function getLoginErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return "Nieprawidłowy e-mail lub hasło.";
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
    <div className="flex flex-col gap-6">
      {error && (
        <DismissibleNotice
          description={getLoginErrorMessage(error)}
          status="danger"
          title="Nie udało się zalogować"
          onDismiss={onDismissError}
        />
      )}

      <form onSubmit={submit}>
        <FormTextField
          isRequired
          inputProps={{
            autoCapitalize: "none",
            autoComplete: "email",
            spellCheck: false,
          }}
          isDisabled={isPending}
          label="E-mail"
          maxLength={200}
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
        />

        <FormTextField
          isRequired
          inputProps={{ autoComplete: "current-password" }}
          isDisabled={isPending}
          label="Hasło"
          maxLength={200}
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
        />

        <Button isPending={isPending} type="submit">
          {isPending ? "Logowanie…" : "Zaloguj się"}
        </Button>
      </form>
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
    <AuthFormLayout
      footer={
        <>
          Nie masz konta? <Link href="/registration">Utwórz je.</Link>
        </>
      }
      title="Witaj ponownie"
    >
      <LoginForm
        error={loginError}
        isPending={isLoggingIn}
        onDismissError={onDismissLoginError}
        onSubmit={onSignIn}
      />
    </AuthFormLayout>
  );
}

export function StaffPanel({
  screen = "dashboard",
}: {
  screen?: "dashboard" | "login";
} = {}) {
  const router = useRouter();
  const { mutate: mutateCache } = useSWRConfig();
  const [selectedWorkspace, setSelectedWorkspace] = useState("orders");
  const [requestedOrderLocationId, setRequestedOrderLocationId] =
    useState<string>();
  const [signOutScope, setSignOutScope] = useState<
    "device" | "everywhere" | null
  >(null);
  const [isPasswordChangeOpen, setIsPasswordChangeOpen] = useState(false);
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
    data: locations,
    error: locationsError,
    mutate: mutateLocations,
  } = useSWR(
    account && screen === "dashboard"
      ? staffLocationsKey(account.accountId)
      : null,
    listLocations,
    { errorRetryCount: 3, shouldRetryOnError },
  );
  const hasNoLocations = locations?.length === 0;
  const workspace =
    hasNoLocations && ["accounts", "integrations"].includes(selectedWorkspace)
      ? "orders"
      : selectedWorkspace;

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
        setIsPasswordChangeOpen(false);
        setSignOutScope(null);
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
    if (isLoggingOut) return;

    const didLogout = await triggerLogout(everywhere);

    if (!didLogout) return;

    setSignOutScope(null);
    await mutateAccount(undefined, { revalidate: false });
    await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
  }

  const isUnauthorized =
    accountError instanceof ApiError && accountError.status === 401;
  const isSignedOut = !account && !accountError && !isLoading;

  useEffect(() => {
    if (screen === "login" && account && !isUnauthorized) {
      router.replace("/dashboard");
    }
    if (screen === "dashboard" && (isUnauthorized || isSignedOut)) {
      router.replace("/login");
    }
  }, [account, isSignedOut, isUnauthorized, router, screen]);

  if (screen === "login" && (isUnauthorized || isSignedOut)) {
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

  if (
    (isLoading && !account) ||
    (screen === "login" && account) ||
    (screen === "dashboard" && (isUnauthorized || isSignedOut))
  ) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner aria-label="Sprawdzanie sesji" />
      </div>
    );
  }

  if (!account) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Alert className="max-w-xl" status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Logowanie jest niedostępne</Alert.Title>
            <Alert.Description>
              {getErrorMessage(accountError)}
            </Alert.Description>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="danger"
                onPress={() => void mutateAccount()}
              >
                Spróbuj ponownie
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

  if (canManageLocations && !locations) {
    return locationsError ? (
      <div className="flex flex-col gap-4 py-12">
        <p role="alert">Nie udało się wczytać lokali. Spróbuj ponownie.</p>
        <Button onPress={() => void mutateLocations()}>Spróbuj ponownie</Button>
      </div>
    ) : (
      <Spinner aria-label="Wczytywanie lokali" />
    );
  }
  const needsFirstLocation = canManageLocations && hasNoLocations;
  const utilities = (
    <div className="panel-workspace-utilities flex shrink-0 items-center gap-2">
      <PanelAppearanceMenu />
      <Dropdown>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Dropdown.Trigger
              aria-label="Konto"
              className="icon-menu-trigger icon-menu-trigger--touch"
              isDisabled={isLoggingOut}
            >
              <AccountIcon aria-hidden="true" size={20} />
            </Dropdown.Trigger>
          </Tooltip.Trigger>
          <Tooltip.Content>Konto</Tooltip.Content>
        </Tooltip>
        <Dropdown.Popover placement="bottom end">
          <Dropdown.Menu
            aria-label="Opcje konta"
            onAction={(key) => {
              if (key === "password") {
                setIsPasswordChangeOpen(true);
              } else {
                resetLogout();
                setSignOutScope(key === "everywhere" ? "everywhere" : "device");
              }
            }}
          >
            <Dropdown.Item id="password">Zmień hasło</Dropdown.Item>
            <Dropdown.Item id="device">Wyloguj się</Dropdown.Item>
            <Dropdown.Item id="everywhere">
              Wyloguj ze wszystkich urządzeń
            </Dropdown.Item>
          </Dropdown.Menu>
        </Dropdown.Popover>
      </Dropdown>
    </div>
  );

  return (
    <div className="flex flex-col gap-6">
      {accountError && (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się odświeżyć konta</Alert.Title>
            <Alert.Description>
              {getErrorMessage(accountError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {canManageAccounts || canManageLocations || canManageIntegrations ? (
        <Tabs
          selectedKey={workspace}
          onSelectionChange={(key) => setSelectedWorkspace(String(key))}
        >
          <div className="panel-workspace-controls">
            <div aria-hidden="true" className="panel-workspace-spacer" />
            <Tabs.ListContainer className="mobile-navigation panel-navigation">
              <Tabs.List
                aria-label="Panel obsługi"
                className="w-full justify-around sm:w-auto"
              >
                <Tabs.Tab id="orders">
                  <span className="tab-motion-content">
                    <OrdersIcon size={18} />
                    <span>Zamówienia</span>
                  </span>
                  <Tabs.Indicator />
                </Tabs.Tab>
                {canManageLocations && (
                  <Tabs.Tab id="locations">
                    <span className="tab-motion-content">
                      <LocationsIcon size={18} />
                      <span>Lokale</span>
                    </span>
                    <Tabs.Indicator />
                  </Tabs.Tab>
                )}
                {canManageAccounts && (
                  <Tabs.Tab id="accounts" isDisabled={hasNoLocations}>
                    <span className="tab-motion-content">
                      <TeamIcon size={18} />
                      <span>Konta</span>
                    </span>
                    <Tabs.Indicator />
                  </Tabs.Tab>
                )}
                {canManageIntegrations && (
                  <Tabs.Tab id="integrations" isDisabled={hasNoLocations}>
                    <span className="tab-motion-content">
                      <IntegrationIcon size={18} />
                      <span>Integracje</span>
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

      {needsFirstLocation && (
        <LocationCreationModal
          key={account.accountId}
          isOpen
          isRequired
          accountId={account.accountId}
          onCreated={(location) => {
            setRequestedOrderLocationId(location.id);
            setSelectedWorkspace("orders");
          }}
          onOpenChange={() => {}}
        />
      )}

      <PasswordChangeDialog
        isOpen={isPasswordChangeOpen}
        onChanged={async () => {
          await mutateAccount(undefined, { revalidate: false });
          await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
        }}
        onOpenChange={setIsPasswordChangeOpen}
      />

      <PanelPopup
        className="sm:max-w-[420px]"
        isOpen={signOutScope !== null}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) {
            setSignOutScope(null);
            if (!isLoggingOut) resetLogout();
          }
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="warning" />
          <PanelPopup.Heading>
            {signOutScope === "everywhere"
              ? "Wyloguj ze wszystkich urządzeń?"
              : "Wyloguj się?"}
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            {signOutScope === "everywhere"
              ? "Nastąpi wylogowanie ze wszystkich urządzeń, również tego."
              : "Nastąpi wylogowanie z tego urządzenia. Aby zarządzać zamówieniami, zaloguj się ponownie."}
          </p>
          {logoutError && (
            <Alert status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>Nie udało się wylogować</Alert.Title>
                <Alert.Description>
                  {getErrorMessage(logoutError)}
                </Alert.Description>
              </Alert.Content>
            </Alert>
          )}
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button slot="close" variant="tertiary">
            Anuluj
          </Button>
          <Button
            isPending={isLoggingOut}
            variant="danger"
            onPress={() => void signOut(signOutScope === "everywhere")}
          >
            {signOutScope === "everywhere"
              ? "Wyloguj ze wszystkich urządzeń"
              : "Wyloguj się"}
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>
    </div>
  );
}

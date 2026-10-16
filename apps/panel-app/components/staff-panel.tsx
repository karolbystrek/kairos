"use client";

import type { FormEvent, ReactNode } from "react";

import { ArrowsClockwiseIcon, SignInIcon } from "@phosphor-icons/react";
import {
  Alert,
  Button,
  Dropdown,
  Link,
  Separator,
  Spinner,
  Tabs,
  Tooltip,
} from "@heroui/react";
import {
  PulseIcon as PlatformIcon,
  ChartBarIcon as AnalyticsIcon,
  ClipboardTextIcon as OrdersIcon,
  UserCircleIcon as AccountIcon,
  MapPinIcon as LocationsIcon,
  ForkKnifeIcon as MenuIcon,
  UsersIcon as TeamIcon,
  FlowArrowIcon as IntegrationIcon,
  XIcon as CloseIcon,
} from "@phosphor-icons/react";
import { useEffect, useState, useSyncExternalStore } from "react";
import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import useSWR, { useSWRConfig } from "swr";
import useSWRMutation from "swr/mutation";

import { FormTextField } from "@/components/form-controls";
import { EmailVerification } from "@/components/email-verification";
import { AuthFormLayout } from "@/components/auth-form-layout";
import { LocationCreationModal } from "@/components/location-creation-modal";
import { ThemeToggle } from "@/components/theme-toggle";
import { subscribeToAuthenticationRequired } from "@/src/api/auth-state";
import { ApiError } from "@/src/api/api-fetch";
import {
  getCurrentAccount,
  getPendingVerification,
  pendingVerificationKey,
  login as loginRequest,
  logout as logoutRequest,
  type LoginCredentials,
} from "@/src/api/authentication";
import { useStaffLocations } from "@/src/api/locations";
import { currentAccountKey, isStaffCacheKey } from "@/src/api/cache-keys";
import { recordPanelAction } from "@/src/api/usage-analytics";
import { errorMessage } from "@/src/messages";

// Staff workspaces load only after sign-in; charts only when an analytics tab opens.
const OrderManagement = dynamic(() =>
  import("@/components/order-management").then(
    (module) => module.OrderManagement,
  ),
);
const AccountManagement = dynamic(() =>
  import("@/components/account-management").then(
    (module) => module.AccountManagement,
  ),
);
const IntegrationManagement = dynamic(() =>
  import("@/components/integration-management").then(
    (module) => module.IntegrationManagement,
  ),
);
const LocationManagement = dynamic(() =>
  import("@/components/location-management").then(
    (module) => module.LocationManagement,
  ),
);
const MenuManagement = dynamic(() =>
  import("@/components/menu-management").then(
    (module) => module.MenuManagement,
  ),
);
const PasswordChangeForm = dynamic(() =>
  import("@/components/password-change-form").then(
    (module) => module.PasswordChangeForm,
  ),
);
const RestaurantAnalytics = dynamic(() =>
  import("@/components/analytics-management").then(
    (module) => module.RestaurantAnalytics,
  ),
);
const PlatformAnalytics = dynamic(() =>
  import("@/components/analytics-management").then(
    (module) => module.PlatformAnalytics,
  ),
);

const logoutKey = ["authentication", "logout"] as const;

const workspaces = [
  { id: "orders", label: "Zamówienia", Icon: OrdersIcon },
  {
    id: "locations",
    label: "Lokale",
    Icon: LocationsIcon,
    capability: "MANAGE_LOCATIONS",
  },
  {
    id: "menus",
    label: "Menu",
    Icon: MenuIcon,
    capability: "MANAGE_MENUS",
    needsLocation: true,
  },
  {
    id: "accounts",
    label: "Konta",
    Icon: TeamIcon,
    capability: "INVITE_OPERATORS",
    needsLocation: true,
  },
  {
    id: "integrations",
    label: "Integracje",
    Icon: IntegrationIcon,
    capability: "MANAGE_EXTERNAL_INTEGRATIONS",
    needsLocation: true,
  },
  {
    id: "analytics",
    label: "Analityka",
    Icon: AnalyticsIcon,
    capability: "VIEW_ANALYTICS",
  },
  {
    id: "platform",
    label: "Platforma",
    Icon: PlatformIcon,
    capability: "VIEW_PLATFORM_ANALYTICS",
  },
] as const satisfies readonly {
  id: string;
  label: string;
  Icon: unknown;
  capability?: string;
  needsLocation?: boolean;
}[];

type WorkspaceId = (typeof workspaces)[number]["id"];

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

function getErrorMessage(error: unknown): string {
  return errorMessage(
    error,
    "Nie udało się wykonać operacji. Sprawdź połączenie i spróbuj ponownie.",
  );
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
          <SignInIcon aria-hidden="true" size={18} />
          {isPending ? "Logowanie…" : "Zaloguj się"}
        </Button>
      </form>
    </div>
  );
}

function SignedOutPanel({
  loginError,
  isLoggingIn,
  passwordChanged,
  onDismissLoginError,
  onSignIn,
}: {
  loginError?: Error;
  isLoggingIn: boolean;
  passwordChanged: boolean;
  onDismissLoginError: () => void;
  onSignIn: (credentials: LoginCredentials) => Promise<void>;
}) {
  const [showPasswordChanged, setShowPasswordChanged] =
    useState(passwordChanged);

  return (
    <AuthFormLayout
      footer={
        <>
          <Link href="/password-reset">Nie pamiętasz hasła?</Link>
          <br />
          Nie masz konta? <Link href="/registration">Utwórz je.</Link>
        </>
      }
      title="Witaj ponownie"
    >
      {showPasswordChanged && (
        <div className="mb-6">
          <DismissibleNotice
            description="Zaloguj się nowym hasłem."
            status="success"
            title="Hasło zostało zmienione"
            onDismiss={() => setShowPasswordChanged(false)}
          />
        </div>
      )}
      <LoginForm
        error={loginError}
        isPending={isLoggingIn}
        onDismissError={onDismissLoginError}
        onSubmit={onSignIn}
      />
    </AuthFormLayout>
  );
}

function subscribeToWorkspace(callback: () => void) {
  window.addEventListener("hashchange", callback);

  return () => window.removeEventListener("hashchange", callback);
}

function getWorkspace() {
  return window.location.hash.slice(1) || "orders";
}

function setSelectedWorkspace(workspace: string) {
  window.location.hash = workspace;
}

export function StaffPanel({
  passwordChanged = false,
  screen = "dashboard",
}: {
  passwordChanged?: boolean;
  screen?: "dashboard" | "login" | "password";
} = {}) {
  const router = useRouter();
  const { mutate: mutateCache } = useSWRConfig();
  const selectedWorkspace = useSyncExternalStore(
    subscribeToWorkspace,
    getWorkspace,
    () => "orders",
  );
  const [orderLocationId, setOrderLocationId] = useState<string>();
  const [menuLocationId, setMenuLocationId] = useState<string>();
  const [hasChangedPassword, setHasChangedPassword] = useState(false);
  const {
    data: account,
    error: accountError,
    isLoading,
    mutate: mutateAccount,
  } = useSWR(currentAccountKey, getCurrentAccount);

  const {
    data: verification,
    error: verificationError,
    isLoading: checkingVerification,
    mutate: mutateVerification,
  } = useSWR(
    !account && !isLoading ? pendingVerificationKey : null,
    getPendingVerification,
    { shouldRetryOnError: false },
  );

  const {
    data: locations,
    error: locationsError,
    mutate: mutateLocations,
  } = useStaffLocations(
    screen === "dashboard" ? account?.accountId : undefined,
  );
  const hasNoLocations = locations?.length === 0;

  const {
    error: loginError,
    isMutating: isLoggingIn,
    reset: resetLogin,
    trigger: triggerLogin,
  } = useSWRMutation(
    currentAccountKey,
    (_, { arg }: { arg: LoginCredentials }) => loginRequest(arg),
    { throwOnError: false },
  );

  const {
    error: logoutError,
    isMutating: isLoggingOut,
    reset: resetLogout,
    trigger: triggerLogout,
  } = useSWRMutation(logoutKey, () => logoutRequest(), {
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
    if ("verificationRequired" in currentAccount) {
      await mutateVerification(currentAccount, { revalidate: false });

      return;
    }
    await mutateVerification(null, { revalidate: false });
    await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
    await mutateAccount(currentAccount, { revalidate: false });
  }

  async function signOut() {
    if (isLoggingOut) return;

    resetLogout();
    const didLogout = await triggerLogout();

    if (!didLogout) return;

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
    if (screen !== "login" && (isUnauthorized || isSignedOut)) {
      router.replace(hasChangedPassword ? "/login?password-changed" : "/login");
    }
  }, [
    account,
    hasChangedPassword,
    isSignedOut,
    isUnauthorized,
    router,
    screen,
  ]);

  if (screen === "login" && checkingVerification) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Spinner aria-label="Sprawdzanie weryfikacji" />
      </div>
    );
  }

  if (screen === "login" && verification) {
    return (
      <EmailVerification
        verification={verification}
        onCancelled={async () => {
          await mutateVerification(null, { revalidate: false });
          resetLogin();
        }}
        onChanged={async (value) => {
          await mutateVerification(value, { revalidate: false });
        }}
        onVerified={async (value) => {
          await mutateVerification(null, { revalidate: false });
          await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
          await mutateAccount(value, { revalidate: false });
        }}
      />
    );
  }
  if (screen === "login" && verificationError) {
    return (
      <AuthFormLayout footer={null} title="Sprawdzanie weryfikacji">
        <p role="alert">Nie udało się sprawdzić stanu weryfikacji.</p>
        <Button onPress={() => void mutateVerification()}>
          Spróbuj ponownie
        </Button>
      </AuthFormLayout>
    );
  }
  if (screen === "login" && (isUnauthorized || isSignedOut)) {
    return (
      <SignedOutPanel
        isLoggingIn={isLoggingIn}
        loginError={loginError}
        passwordChanged={passwordChanged}
        onDismissLoginError={resetLogin}
        onSignIn={signIn}
      />
    );
  }

  if (
    (isLoading && !account) ||
    (screen === "login" && account) ||
    (screen !== "login" && (isUnauthorized || isSignedOut))
  ) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Spinner aria-label="Sprawdzanie sesji" />
      </div>
    );
  }

  if (!account) {
    return (
      <div className="flex flex-1 items-center justify-center">
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
                <ArrowsClockwiseIcon aria-hidden="true" size={18} /> Spróbuj
                ponownie
              </Button>
            </div>
          </Alert.Content>
        </Alert>
      </div>
    );
  }

  if (screen === "password") {
    return (
      <PasswordChangeForm
        onChanged={async () => {
          // Password change ends every session, including this one.
          setHasChangedPassword(true);
          await mutateAccount(undefined, { revalidate: false });
          await mutateCache(isStaffCacheKey, undefined, { revalidate: false });
        }}
      />
    );
  }

  const canManageLocations = account.capabilities.includes("MANAGE_LOCATIONS");
  const canViewTenantOrders =
    account.capabilities.includes("VIEW_TENANT_ORDERS");
  const availableWorkspaces = workspaces.filter(
    (workspace) =>
      !("capability" in workspace) ||
      account.capabilities.includes(workspace.capability),
  );
  const visibleWorkspace: WorkspaceId =
    availableWorkspaces.find(
      (workspace) =>
        workspace.id === selectedWorkspace &&
        !(hasNoLocations && "needsLocation" in workspace),
    )?.id ?? "orders";

  const needsFirstLocation = canManageLocations && hasNoLocations;
  const panels: Record<WorkspaceId, ReactNode> = {
    orders: (
      <OrderManagement
        key={`orders-${account.accountId}`}
        accountId={account.accountId}
        canManageLocations={canManageLocations}
        canViewTenantOrders={canViewTenantOrders}
        selectedLocationId={orderLocationId}
        onSelectLocation={setOrderLocationId}
      />
    ),
    locations: (
      <LocationManagement
        key={`locations-${account.accountId}`}
        accountId={account.accountId}
        onEditMenu={(locationId) => {
          setMenuLocationId(locationId);
          recordPanelAction("menu_editor_opened");
          setSelectedWorkspace("menus");
        }}
        onViewOrders={(locationId) => {
          setOrderLocationId(locationId);
          setSelectedWorkspace("orders");
        }}
      />
    ),
    menus: (
      <MenuManagement
        key={`menus-${account.accountId}`}
        accountId={account.accountId}
        selectedLocationId={menuLocationId}
        onSelectLocation={setMenuLocationId}
      />
    ),
    accounts: (
      <AccountManagement
        key={`accounts-${account.accountId}`}
        account={account}
      />
    ),
    integrations: (
      <IntegrationManagement
        key={`integrations-${account.accountId}`}
        accountId={account.accountId}
      />
    ),
    analytics: (
      <RestaurantAnalytics
        key={`analytics-${account.accountId}`}
        account={account}
        onAccessLost={() => void mutateAccount()}
      />
    ),
    platform: (
      <PlatformAnalytics
        key={`platform-${account.accountId}`}
        accountId={account.accountId}
        onAccessLost={() => void mutateAccount()}
      />
    ),
  };
  const utilities = (
    <div className="panel-workspace-utilities flex shrink-0 items-center gap-2">
      <ThemeToggle className="theme-toggle--touch" />
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
        <Dropdown.Popover
          className="max-w-[calc(100svw-2rem)]"
          placement="bottom end"
        >
          <Dropdown.Menu
            aria-label="Opcje konta"
            onAction={(key) => {
              if (key === "password") {
                router.push("/dashboard/password");
              } else {
                void signOut();
              }
            }}
          >
            <Dropdown.Item id="password">Zmień hasło</Dropdown.Item>
            <Separator />
            <Dropdown.Item id="logout" variant="danger">
              Wyloguj się
            </Dropdown.Item>
          </Dropdown.Menu>
        </Dropdown.Popover>
      </Dropdown>
    </div>
  );

  return (
    <div className="flex flex-col gap-6">
      {logoutError && (
        <DismissibleNotice
          description={getErrorMessage(logoutError)}
          status="danger"
          title="Nie udało się wylogować"
          onDismiss={resetLogout}
        />
      )}

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

      {canManageLocations && !locations && locationsError && (
        <div className="flex flex-col items-start gap-4">
          <p role="alert">Nie udało się wczytać lokali. Spróbuj ponownie.</p>
          <Button onPress={() => void mutateLocations()}>
            <ArrowsClockwiseIcon aria-hidden="true" size={18} /> Spróbuj
            ponownie
          </Button>
        </div>
      )}

      {availableWorkspaces.length > 1 ? (
        <Tabs
          selectedKey={visibleWorkspace}
          onSelectionChange={(key) => {
            const next = String(key);

            if (next === visibleWorkspace) return;
            if (next === "menus") recordPanelAction("menu_editor_opened");
            if (next === "integrations")
              recordPanelAction("integration_settings_opened");
            setSelectedWorkspace(next);
          }}
        >
          <div className="panel-workspace-controls">
            <div aria-hidden="true" className="panel-workspace-spacer" />
            <Tabs.ListContainer className="mobile-navigation panel-navigation">
              <Tabs.List
                aria-label="Panel obsługi"
                className="w-full justify-around sm:w-auto"
              >
                {availableWorkspaces.map((workspace) => (
                  <Tabs.Tab
                    key={workspace.id}
                    id={workspace.id}
                    isDisabled={hasNoLocations && "needsLocation" in workspace}
                  >
                    <span className="tab-motion-content">
                      <workspace.Icon size={18} />
                      <span>{workspace.label}</span>
                    </span>
                    <Tabs.Indicator />
                  </Tabs.Tab>
                ))}
              </Tabs.List>
            </Tabs.ListContainer>
            {utilities}
          </div>
          {availableWorkspaces.map(({ id }) => (
            <Tabs.Panel key={id} className="pt-6" id={id}>
              {panels[id]}
            </Tabs.Panel>
          ))}
        </Tabs>
      ) : (
        <>
          <div className="flex justify-end">{utilities}</div>
          <OrderManagement
            key={account.accountId}
            accountId={account.accountId}
            canManageLocations={false}
            canViewTenantOrders={canViewTenantOrders}
            selectedLocationId={orderLocationId}
            onSelectLocation={setOrderLocationId}
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
            setOrderLocationId(location.id);
            setSelectedWorkspace("orders");
          }}
          onOpenChange={() => {}}
        />
      )}
    </div>
  );
}

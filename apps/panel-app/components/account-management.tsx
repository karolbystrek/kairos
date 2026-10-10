"use client";

import type { FormEvent } from "react";
import type { TenantAccount } from "@/src/api/authentication";

import {
  Alert,
  Button,
  ListBox,
  Radio,
  RadioGroup,
  Spinner,
  Label,
  Tooltip,
} from "@heroui/react";
import {
  ArrowRight as ArrowRightIcon,
  Ban as DisableIcon,
  Check as CheckIcon,
  Plus as PlusIcon,
  Trash2 as DeleteIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";

import { PanelPopup } from "@/components/panel-popup";
import { FormSelect, FormTextField } from "@/components/form-controls";
import { OneTimeSecret } from "@/components/integrations/one-time-secret";
import { PanelCard } from "@/components/panel-card";
import { PanelDetailHeader } from "@/components/panel-detail-header";
import {
  createAccountInvitation,
  listAccountInvitations,
  revokeAccountInvitation,
  type AccountInvitation,
  type CreatedAccountInvitation,
  type CreateAccountInvitationInput,
} from "@/src/api/account-invitations";
import {
  deleteManagedAccount,
  listManagedAccounts,
  updateManagedAccountStatus,
  type AssignmentRole,
  type ManagedAccount,
} from "@/src/api/accounts";
import { requiredEmailInputSchema } from "@/src/api/account-input";
import { ApiError } from "@/src/api/api-fetch";
import {
  staffAccountsKey,
  staffInvitationsKey,
  staffLocationsKey,
} from "@/src/api/cache-keys";
import { listLocations, type Location } from "@/src/api/locations";

type StatusMutationInput = {
  accountId: string;
  status: "ENABLED" | "DISABLED";
};

function createInvitationMutation(
  _key: ReturnType<typeof staffInvitationsKey>,
  { arg }: { arg: CreateAccountInvitationInput },
): Promise<CreatedAccountInvitation> {
  return createAccountInvitation(arg);
}

function revokeInvitationMutation(
  _key: ReturnType<typeof staffInvitationsKey>,
  { arg }: { arg: string },
): Promise<boolean> {
  return revokeAccountInvitation(arg);
}

function statusMutation(
  _key: ReturnType<typeof staffAccountsKey>,
  { arg }: { arg: StatusMutationInput },
): Promise<ManagedAccount> {
  return updateManagedAccountStatus(arg.accountId, arg.status);
}

function deleteMutation(
  _key: ReturnType<typeof staffAccountsKey>,
  { arg }: { arg: string },
): Promise<boolean> {
  return deleteManagedAccount(arg);
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;

  return "Nie udało się wykonać operacji na koncie. Sprawdź połączenie i spróbuj ponownie.";
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

function LocationSelect({
  locations,
  onChange,
  selectedId,
}: {
  locations: Location[];
  onChange: (locationId: string) => void;
  selectedId?: string;
}) {
  return (
    <FormSelect
      fullWidth
      className="min-w-0 max-w-full"
      label="Lokal"
      selectedKey={selectedId}
      onSelectionChange={(key) => {
        if (key !== null) onChange(String(key));
      }}
    >
      <ListBox items={locations}>
        {(location) => (
          <ListBox.Item id={location.id} textValue={location.name}>
            {location.name}
          </ListBox.Item>
        )}
      </ListBox>
    </FormSelect>
  );
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("pl-PL", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function AccountManagement({ account }: { account: TenantAccount }) {
  const isAdministrator = account.tenantRole === "ADMIN";
  const [selectedAccountId, setSelectedAccountId] = useState<string>();
  const [accountToDisable, setAccountToDisable] = useState<ManagedAccount>();
  const [accountToDelete, setAccountToDelete] = useState<ManagedAccount>();
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedInvitationId, setSelectedInvitationId] = useState<string>();
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string>();
  const [invitationToRevoke, setInvitationToRevoke] =
    useState<AccountInvitation>();
  const [createdInvitation, setCreatedInvitation] =
    useState<CreatedAccountInvitation>();
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const [role, setRole] = useState<AssignmentRole>("OPERATOR");
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 60_000);

    return () => window.clearInterval(interval);
  }, []);

  const {
    data: locations = [],
    error: locationsError,
    isLoading: areLocationsLoading,
  } = useSWR(staffLocationsKey(account.accountId), () => listLocations(), {
    errorRetryCount: 3,
    shouldRetryOnError,
  });
  const enabledLocations = locations.filter(
    (location) => location.status === "ENABLED",
  );
  const {
    data: accounts = [],
    error: accountsError,
    isLoading: areAccountsLoading,
    mutate: mutateAccounts,
  } = useSWR(staffAccountsKey(account.accountId), listManagedAccounts, {
    errorRetryCount: 3,
    shouldRetryOnError,
  });
  const {
    data: invitations = [],
    error: invitationsError,
    isLoading: areInvitationsLoading,
    mutate: mutateInvitations,
  } = useSWR(staffInvitationsKey(account.accountId), listAccountInvitations, {
    errorRetryCount: 3,
    shouldRetryOnError,
  });
  const {
    error: creationError,
    isMutating: isCreating,
    reset: resetCreation,
    trigger: triggerCreation,
  } = useSWRMutation(
    staffInvitationsKey(account.accountId),
    createInvitationMutation,
    { throwOnError: false },
  );
  const {
    error: revocationError,
    isMutating: isRevoking,
    reset: resetRevocation,
    trigger: triggerRevocation,
  } = useSWRMutation(
    staffInvitationsKey(account.accountId),
    revokeInvitationMutation,
    { throwOnError: false },
  );
  const {
    error: statusError,
    isMutating: isChangingStatus,
    reset: resetStatus,
    trigger: triggerStatus,
  } = useSWRMutation(staffAccountsKey(account.accountId), statusMutation, {
    throwOnError: false,
  });
  const {
    error: deletionError,
    isMutating: isDeleting,
    reset: resetDeletion,
    trigger: triggerDeletion,
  } = useSWRMutation(staffAccountsKey(account.accountId), deleteMutation, {
    throwOnError: false,
  });

  const assignedLocationId = account.assignment?.locationId;
  const locationId = isAdministrator
    ? enabledLocations.some((location) => location.id === selectedLocationId)
      ? selectedLocationId
      : enabledLocations[0]?.id
    : assignedLocationId;
  const selectedRole = isAdministrator ? role : "OPERATOR";
  const selectedAccount = accounts.find(
    (candidate) => candidate.id === selectedAccountId,
  );
  const locationNames = new Map(
    locations.map((location) => [location.id, location.name]),
  );
  const pendingInvitations = invitations.filter(
    (invitation) => new Date(invitation.expiresAt).getTime() > now,
  );
  const selectedInvitation = pendingInvitations.find(
    (invitation) => invitation.id === selectedInvitationId,
  );
  const collectionError = locationsError ?? accountsError;

  async function submitInvitation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!locationId || isCreating) return;
    const parsedEmail = requiredEmailInputSchema.safeParse(email);

    if (!parsedEmail.success) {
      setEmailError(parsedEmail.error.issues[0]?.message);

      return;
    }
    setEmailError(undefined);
    resetCreation();
    const created = await triggerCreation({
      email: parsedEmail.data,
      locationId,
      role: selectedRole,
    });

    if (!created) return;
    setCreatedInvitation(created);
    setIsCreateOpen(true);
    await mutateInvitations((current) => [created, ...(current ?? [])], {
      revalidate: false,
    });
    void mutateInvitations(undefined, { throwOnError: false });
  }

  async function revokeInvitation(): Promise<void> {
    if (!invitationToRevoke || isRevoking) return;
    resetRevocation();
    const revoked = await triggerRevocation(invitationToRevoke.id);

    if (!revoked) return;
    await mutateInvitations(
      (current) =>
        (current ?? []).filter(
          (invitation) => invitation.id !== invitationToRevoke.id,
        ),
      { revalidate: false },
    );
    setInvitationToRevoke(undefined);
    setSelectedInvitationId(undefined);
    void mutateInvitations(undefined, { throwOnError: false });
  }

  async function changeStatus(
    managedAccount: ManagedAccount,
  ): Promise<boolean> {
    if (isChangingStatus) return false;

    resetStatus();
    const updated = await triggerStatus({
      accountId: managedAccount.id,
      status: managedAccount.status === "ENABLED" ? "DISABLED" : "ENABLED",
    });

    if (!updated) return false;
    await mutateAccounts(
      (current) =>
        (current ?? [updated]).map((candidate) =>
          candidate.id === updated.id ? updated : candidate,
        ),
      { revalidate: false },
    );
    if (updated.status === "DISABLED") {
      void mutateInvitations(undefined, { throwOnError: false });
    }

    return true;
  }

  async function removeAccount(): Promise<boolean> {
    if (!accountToDelete || isDeleting) return false;

    resetDeletion();
    const deleted = await triggerDeletion(accountToDelete.id);

    if (!deleted) return false;
    await mutateAccounts(
      (current) =>
        (current ?? []).filter(
          (candidate) => candidate.id !== accountToDelete.id,
        ),
      { revalidate: false },
    );
    setSelectedAccountId(undefined);
    setAccountToDelete(undefined);
    setDeleteConfirmation("");
    void mutateAccounts(undefined, { throwOnError: false });
    void mutateInvitations(undefined, { throwOnError: false });

    return true;
  }

  function openCreate() {
    if (isCreating || createdInvitation) {
      setIsCreateOpen(true);

      return;
    }
    resetCreation();
    setSelectedLocationId(locationId ?? enabledLocations[0]?.id);
    setRole("OPERATOR");
    setEmail("");
    setEmailError(undefined);
    setIsCreateOpen(true);
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-4">
          <h1 className="page-title">Konta</h1>
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                aria-label="Nowe konto"
                className="rounded-md"
                isDisabled={enabledLocations.length === 0}
                size="lg"
                onPress={openCreate}
              >
                <PlusIcon size={20} />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>
              {enabledLocations.length === 0
                ? "Włącz lokal, aby zaprosić użytkownika"
                : "Nowe konto"}
            </Tooltip.Content>
          </Tooltip>
        </div>
      </div>

      {collectionError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wykonać operacji na koncie</Alert.Title>
            <Alert.Description>
              {getErrorMessage(collectionError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {invitationsError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wczytać zaproszeń</Alert.Title>
            <Alert.Description>
              {getErrorMessage(invitationsError)}
            </Alert.Description>
            <Button
              className="mt-3"
              size="sm"
              variant="danger"
              onPress={() => void mutateInvitations()}
            >
              Spróbuj ponownie
            </Button>
          </Alert.Content>
        </Alert>
      )}
      {areInvitationsLoading && (
        <div className="flex items-center gap-2 text-sm text-muted">
          <Spinner aria-label="Wczytywanie zaproszeń" size="sm" />
          <span>Wczytywanie zaproszeń…</span>
        </div>
      )}

      {!areLocationsLoading && enabledLocations.length === 0 && (
        <p className="text-sm text-muted">
          Włącz lokal w zakładce Lokale, aby zaprosić użytkownika.
        </p>
      )}

      {areAccountsLoading &&
      accounts.length === 0 &&
      pendingInvitations.length === 0 ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Wczytywanie kont" />
        </div>
      ) : accounts.length === 0 &&
        pendingInvitations.length === 0 &&
        !areInvitationsLoading &&
        !invitationsError ? (
        <div className="py-12">
          <h2 className="section-title">Brak kont pracowników</h2>
          <p className="mt-2 max-w-sm secondary-text">
            {isAdministrator
              ? "Zaproś kierownika lub pracownika do istniejącego lokalu."
              : "Zaproś pracownika do swojego lokalu."}
          </p>
        </div>
      ) : (
        <>
          <section
            aria-label="Konta i zaproszenia"
            className="entity-card-grid"
          >
            {accounts.map((managedAccount) => (
              <PanelCard
                key={managedAccount.id}
                accessibilityLabel={`Pokaż konto ${managedAccount.email}, ${managedAccount.status === "ENABLED" ? "włączone" : "wyłączone"}`}
                isSelected={managedAccount.id === selectedAccount?.id}
                metadata={
                  <p className="mt-2 break-words text-sm text-muted">
                    {managedAccount.role === "MANAGER"
                      ? "Kierownik"
                      : "Pracownik"}{" "}
                    ·{" "}
                    {locationNames.get(managedAccount.locationId) ??
                      "Niedostępny"}
                  </p>
                }
                status={managedAccount.status}
                title={managedAccount.email}
                trailing={<ArrowRightIcon size={17} />}
                onPress={() => {
                  setSelectedInvitationId(undefined);
                  resetStatus();
                  setSelectedAccountId(managedAccount.id);
                }}
              />
            ))}
            {pendingInvitations.map((invitation) => (
              <PanelCard
                key={invitation.id}
                accessibilityLabel={`Pokaż zaproszenie dla ${invitation.email}, oczekujące`}
                metadata={
                  <p className="mt-2 break-words text-sm text-muted">
                    {invitation.role === "MANAGER" ? "Kierownik" : "Pracownik"}{" "}
                    · {invitation.locationName}
                  </p>
                }
                status="PENDING"
                title={invitation.email}
                trailing={<ArrowRightIcon size={17} />}
                onPress={() => {
                  setSelectedAccountId(undefined);
                  setSelectedInvitationId(invitation.id);
                }}
              />
            ))}
          </section>

          {selectedAccount && (
            <PanelPopup
              isOpen
              aria-label={`Konto ${selectedAccount.email}`}
              onOpenChange={(open) => {
                if (!open) setSelectedAccountId(undefined);
              }}
            >
              <PanelPopup.Body className="pb-6 pt-12">
                <PanelDetailHeader
                  eyebrow="Konto"
                  title={selectedAccount.email}
                  trailingActions={
                    <>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label={`${selectedAccount.status === "ENABLED" ? "Wyłącz" : "Włącz"} konto ${selectedAccount.email}`}
                            className="shrink-0 rounded-md"
                            isPending={isChangingStatus}
                            variant={
                              selectedAccount.status === "ENABLED"
                                ? "danger"
                                : "secondary"
                            }
                            onPress={() => {
                              if (isChangingStatus) return;
                              if (selectedAccount.status === "ENABLED") {
                                setAccountToDisable(selectedAccount);
                              } else {
                                void changeStatus(selectedAccount);
                              }
                            }}
                          >
                            {selectedAccount.status === "ENABLED" ? (
                              <DisableIcon size={20} />
                            ) : (
                              <CheckIcon size={20} />
                            )}
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                          {selectedAccount.status === "ENABLED"
                            ? "Wyłącz"
                            : "Włącz"}
                        </Tooltip.Content>
                      </Tooltip>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label={`Usuń konto ${selectedAccount.email}`}
                            className="shrink-0 rounded-md"
                            variant="danger"
                            onPress={() => {
                              if (isDeleting) return;
                              resetDeletion();
                              setDeleteConfirmation("");
                              setAccountToDelete(selectedAccount);
                            }}
                          >
                            <DeleteIcon size={20} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>Usuń</Tooltip.Content>
                      </Tooltip>
                    </>
                  }
                />

                <p className="mt-4 text-sm text-muted">
                  Status:{" "}
                  {selectedAccount.status === "ENABLED"
                    ? "Włączone"
                    : "Wyłączone"}
                </p>
                {statusError && (
                  <Alert status="danger">
                    <Alert.Content>
                      <Alert.Title>
                        Nie udało się zmienić statusu konta
                      </Alert.Title>
                      <Alert.Description>
                        {getErrorMessage(statusError)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
                <dl className="mt-8 grid gap-5 border-t border-separator pt-6 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                      Rola
                    </dt>
                    <dd className="mt-1 font-medium">
                      {selectedAccount.role === "MANAGER"
                        ? "Kierownik"
                        : "Pracownik"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                      Lokal
                    </dt>
                    <dd className="mt-1 font-medium">
                      {locationNames.get(selectedAccount.locationId) ??
                        "Niedostępny"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                      E-mail
                    </dt>
                    <dd className="mt-1 break-words font-medium">
                      {selectedAccount.email}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                      Utworzono
                    </dt>
                    <dd className="mt-1 font-medium">
                      {new Date(selectedAccount.createdAt).toLocaleDateString(
                        "pl-PL",
                      )}
                    </dd>
                  </div>
                </dl>
              </PanelPopup.Body>
            </PanelPopup>
          )}
        </>
      )}

      <PanelPopup
        className="min-w-0"
        isOpen={isCreateOpen}
        size="lg"
        onOpenChange={setIsCreateOpen}
      >
        <PanelPopup.Header>
          <PanelPopup.Heading>
            {createdInvitation ? "Link zaproszenia" : "Nowe konto"}
          </PanelPopup.Heading>
        </PanelPopup.Header>
        {createdInvitation ? (
          <PanelPopup.Body className="min-w-0 max-w-full pb-6">
            <OneTimeSecret
              secret={{
                title: "Udostępnij zaproszenie",
                description: `${createdInvitation.email} · ${createdInvitation.role === "MANAGER" ? "Kierownik" : "Pracownik"} · ${createdInvitation.locationName}`,
                value: createdInvitation.invitationLink,
              }}
              onConfirmed={() => {
                setCreatedInvitation(undefined);
                setIsCreateOpen(false);
              }}
            />
          </PanelPopup.Body>
        ) : (
          <form className="min-w-0 max-w-full" onSubmit={submitInvitation}>
            <PanelPopup.Body className="flex min-w-0 max-w-full flex-col gap-4">
              {creationError && (
                <Alert status="danger">
                  <Alert.Indicator />
                  <Alert.Content>
                    <Alert.Title>
                      Nie udało się utworzyć zaproszenia
                    </Alert.Title>
                    <Alert.Description>
                      {getErrorMessage(creationError)}
                    </Alert.Description>
                  </Alert.Content>
                </Alert>
              )}
              <FormTextField
                fullWidth
                isRequired
                errorMessage={emailError}
                inputProps={{ type: "email", autoComplete: "email" }}
                isDisabled={isCreating}
                label="E-mail zapraszanej osoby"
                maxLength={200}
                name="invitation-email"
                value={email}
                onChange={(value) => {
                  setEmail(value);
                  setEmailError(undefined);
                }}
              />
              {isAdministrator ? (
                <LocationSelect
                  locations={enabledLocations}
                  selectedId={locationId}
                  onChange={setSelectedLocationId}
                />
              ) : (
                <div className="min-w-0 max-w-full">
                  <p className="text-sm font-medium">Lokal</p>
                  <p className="mt-1 break-words text-sm text-muted">
                    {
                      locations.find((location) => location.id === locationId)
                        ?.name
                    }
                  </p>
                </div>
              )}
              {isAdministrator && (
                <RadioGroup
                  className="min-w-0 max-w-full"
                  name="account-role"
                  orientation="horizontal"
                  value={role}
                  onChange={(value) =>
                    setRole(value === "MANAGER" ? "MANAGER" : "OPERATOR")
                  }
                >
                  <Label>Rola</Label>
                  <Radio value="OPERATOR">
                    <Radio.Content>
                      <Radio.Control>
                        <Radio.Indicator />
                      </Radio.Control>
                      Pracownik
                    </Radio.Content>
                  </Radio>
                  <Radio value="MANAGER">
                    <Radio.Content>
                      <Radio.Control>
                        <Radio.Indicator />
                      </Radio.Control>
                      Kierownik
                    </Radio.Content>
                  </Radio>
                </RadioGroup>
              )}
            </PanelPopup.Body>
            <PanelPopup.Footer className="max-w-full flex-wrap">
              <Button slot="close" variant="tertiary">
                Anuluj
              </Button>
              <Button isPending={isCreating} type="submit">
                <PlusIcon size={18} />
                {isCreating ? "Tworzenie…" : "Utwórz zaproszenie"}
              </Button>
            </PanelPopup.Footer>
          </form>
        )}
      </PanelPopup>

      {selectedInvitation && (
        <PanelPopup
          isOpen
          aria-label={`Zaproszenie dla ${selectedInvitation.email}`}
          onOpenChange={(open) => {
            if (!open) setSelectedInvitationId(undefined);
          }}
        >
          <PanelPopup.Body className="pb-6 pt-12">
            <PanelDetailHeader
              eyebrow="Zaproszenie"
              title={selectedInvitation.email}
              trailingActions={
                <Button
                  isIconOnly
                  aria-label={`Unieważnij zaproszenie dla ${selectedInvitation.email}`}
                  variant="danger"
                  onPress={() => {
                    if (isRevoking) return;
                    resetRevocation();
                    setInvitationToRevoke(selectedInvitation);
                  }}
                >
                  <DisableIcon size={20} />
                </Button>
              }
            />
            <dl className="mt-6 grid gap-5 border-t border-separator pt-6 sm:grid-cols-2">
              <div>
                <dt className="text-sm text-muted">Status</dt>
                <dd className="mt-1 font-medium">Oczekujące</dd>
              </div>
              <div>
                <dt className="text-sm text-muted">Rola</dt>
                <dd className="mt-1 font-medium">
                  {selectedInvitation.role === "MANAGER"
                    ? "Kierownik"
                    : "Pracownik"}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted">Lokal</dt>
                <dd className="mt-1 break-words font-medium">
                  {selectedInvitation.locationName}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted">Autor</dt>
                <dd className="mt-1 break-words font-medium">
                  {selectedInvitation.issuedByEmail}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted">Utworzono</dt>
                <dd className="mt-1 font-medium">
                  {formatDateTime(selectedInvitation.createdAt)}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted">Wygasa</dt>
                <dd className="mt-1 font-medium">
                  {formatDateTime(selectedInvitation.expiresAt)}
                </dd>
              </div>
            </dl>
          </PanelPopup.Body>
        </PanelPopup>
      )}

      <PanelPopup
        className="sm:max-w-[440px]"
        isOpen={Boolean(invitationToRevoke)}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) setInvitationToRevoke(undefined);
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="danger" />
          <PanelPopup.Heading>Unieważnić zaproszenie?</PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            Zaproszenie dla {invitationToRevoke?.email} do lokalu{" "}
            {invitationToRevoke?.locationName}
            (rola:{" "}
            {invitationToRevoke?.role === "MANAGER" ? "Kierownik" : "Pracownik"}
            ) natychmiast przestanie działać.
          </p>
          {revocationError && (
            <Alert status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>Nie udało się unieważnić zaproszenia</Alert.Title>
                <Alert.Description>
                  {getErrorMessage(revocationError)}
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
            isPending={isRevoking}
            variant="danger"
            onPress={() => void revokeInvitation()}
          >
            Unieważnij
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>

      <PanelPopup
        className="sm:max-w-[420px]"
        isOpen={Boolean(accountToDisable)}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) setAccountToDisable(undefined);
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="warning" />
          <PanelPopup.Heading>
            Wyłączyć konto {accountToDisable?.email}?
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            Konto zostanie wylogowane, a jego oczekujące zaproszenia
            unieważnione. Dostęp do panelu zostanie zablokowany do ponownego
            włączenia konta.
          </p>
          {statusError && (
            <Alert status="danger">
              <Alert.Content>
                <Alert.Title>Nie udało się wyłączyć konta</Alert.Title>
                <Alert.Description>
                  {getErrorMessage(statusError)}
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
            isPending={isChangingStatus}
            variant="danger"
            onPress={() => {
              if (!accountToDisable) return;
              void changeStatus(accountToDisable).then((changed) => {
                if (changed) setAccountToDisable(undefined);
              });
            }}
          >
            Wyłącz
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>

      <PanelPopup
        className="sm:max-w-[460px]"
        isOpen={Boolean(accountToDelete)}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) {
            setAccountToDelete(undefined);
            setDeleteConfirmation("");
            if (!isDeleting) resetDeletion();
          }
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="danger" />
          <PanelPopup.Heading>
            Usunąć konto {accountToDelete?.email}?
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            Konto zostanie wylogowane, zniknie z listy kont i straci oczekujące
            zaproszenia oraz dostęp do panelu. Aby potwierdzić, wpisz dokładny
            adres e-mail konta.
          </p>
          <FormTextField
            fullWidth
            isRequired
            inputProps={{ autoComplete: "off" }}
            isDisabled={isDeleting}
            label={`Wpisz ${accountToDelete?.email} w celu potwierdzenia`}
            name="delete-account-confirmation"
            value={deleteConfirmation}
            onChange={setDeleteConfirmation}
          />
          {deletionError && (
            <Alert status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>Nie udało się usunąć konta</Alert.Title>
                <Alert.Description>
                  {getErrorMessage(deletionError)}
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
            isDisabled={deleteConfirmation !== accountToDelete?.email}
            isPending={isDeleting}
            variant="danger"
            onPress={() => void removeAccount()}
          >
            Usuń
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>
    </div>
  );
}

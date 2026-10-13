"use client";

import type { FormEvent } from "react";
import type { TenantAccount } from "@/src/api/authentication";

import { ArrowsClockwiseIcon } from "@phosphor-icons/react";
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
  ProhibitIcon as DisableIcon,
  CheckIcon,
  PlusIcon,
  TrashIcon as DeleteIcon,
} from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";

import { HoldToConfirmButton } from "@/components/hold-to-confirm-button";
import { PanelPopup } from "@/components/panel-popup";
import { FormSelect, FormTextField } from "@/components/form-controls";
import { PanelCard } from "@/components/panel-card";
import { PanelDetailHeader } from "@/components/panel-detail-header";
import {
  createAccountInvitation,
  listAccountInvitations,
  revokeAccountInvitation,
  resendAccountInvitation,
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

function resendInvitationMutation(
  _key: ReturnType<typeof staffInvitationsKey>,
  { arg }: { arg: string },
): Promise<AccountInvitation> {
  return resendAccountInvitation(arg);
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

const invitationStates = {
  PENDING: "Oczekujące",
  REDEEMED: "Wykorzystane",
  REVOKED: "Unieważnione",
  REPLACED: "Zastąpione",
  EXPIRED: "Wygasłe",
};
const deliveryStates = {
  QUEUED: "E-mail w kolejce",
  SENT: "E-mail wysłany",
  FAILED: "Wysyłka nieudana",
};

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("pl-PL", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function AccountManagement({ account }: { account: TenantAccount }) {
  const isAdministrator = account.tenantRole === "ADMIN";
  const [selectedAccountId, setSelectedAccountId] = useState<string>();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedInvitationId, setSelectedInvitationId] = useState<string>();
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string>();
  const [createdInvitation, setCreatedInvitation] =
    useState<CreatedAccountInvitation>();
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const [role, setRole] = useState<AssignmentRole>("OPERATOR");
  const [replacementInput, setReplacementInput] =
    useState<CreateAccountInvitationInput>();
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
    error: resendError,
    isMutating: isResending,
    reset: resetResend,
    trigger: triggerResend,
  } = useSWRMutation(
    staffInvitationsKey(account.accountId),
    resendInvitationMutation,
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
  const selectedInvitation = invitations.find(
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
    await issueInvitation({
      email: parsedEmail.data,
      locationId,
      role: selectedRole,
      replaceExisting: false,
    });
  }

  async function issueInvitation(
    input: CreateAccountInvitationInput,
  ): Promise<boolean> {
    if (isCreating) return false;
    resetCreation();
    try {
      const created = await triggerCreation(input, { throwOnError: true });

      if (!created) return false;
      setReplacementInput(undefined);
      setCreatedInvitation(created);
      setIsCreateOpen(true);
      await mutateInvitations(undefined, { throwOnError: false });

      return true;
    } catch (error) {
      if (error instanceof ApiError && error.status === 409)
        setReplacementInput(input);

      return false;
    }
  }

  async function revokeInvitation(
    invitationToRevoke: AccountInvitation,
  ): Promise<void> {
    if (isRevoking || isResending) return;
    resetRevocation();
    const revoked = await triggerRevocation(invitationToRevoke.id);

    if (!revoked) return;
    setSelectedInvitationId(undefined);
    await mutateInvitations(undefined, { throwOnError: false });
  }

  async function resendInvitation(
    invitation: AccountInvitation,
  ): Promise<boolean> {
    if (isResending || isRevoking) return false;
    resetResend();
    const replacement = await triggerResend(invitation.id);

    if (!replacement) return false;
    setSelectedInvitationId(undefined);
    setCreatedInvitation(replacement);
    setIsCreateOpen(true);
    await mutateInvitations(undefined, { throwOnError: false });

    return true;
  }

  async function changeStatus(
    managedAccount: ManagedAccount,
  ): Promise<boolean> {
    if (isChangingStatus || isDeleting) return false;

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

  async function removeAccount(
    accountToDelete: ManagedAccount,
  ): Promise<boolean> {
    if (isDeleting || isChangingStatus) return false;

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
    setReplacementInput(undefined);
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
          <h1 className="sr-only">Konta</h1>
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                aria-label="Nowe konto"
                className="rounded-md"
                isDisabled={enabledLocations.length === 0}
                size="lg"
                onPress={openCreate}
              >
                <PlusIcon size={20} />
                Nowe konto
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
              aria-label="Spróbuj ponownie wczytać zaproszenia"
              className="mt-3"
              size="sm"
              variant="danger"
              onPress={() => void mutateInvitations()}
            >
              <ArrowsClockwiseIcon aria-hidden="true" size={18} /> Spróbuj
              ponownie
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
      invitations.length === 0 ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Wczytywanie kont" />
        </div>
      ) : accounts.length === 0 &&
        invitations.length === 0 &&
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
                onPress={() => {
                  setSelectedInvitationId(undefined);
                  resetStatus();
                  resetDeletion();
                  setSelectedAccountId(managedAccount.id);
                }}
              />
            ))}
            {invitations.map((invitation) => (
              <PanelCard
                key={invitation.id}
                accessibilityLabel={`Pokaż zaproszenie dla ${invitation.email}, ${invitationStates[invitation.state]}`}
                metadata={
                  <p className="mt-2 break-words text-sm text-muted">
                    {invitation.role === "MANAGER" ? "Kierownik" : "Pracownik"}{" "}
                    · {invitation.locationName}
                    <br />
                    {invitationStates[invitation.state]} ·{" "}
                    {deliveryStates[invitation.deliveryState]}
                    <br />
                    {formatDateTime(invitation.createdAt)}
                  </p>
                }
                status={invitation.state === "PENDING" ? "PENDING" : undefined}
                title={invitation.email}
                onPress={() => {
                  setSelectedAccountId(undefined);
                  resetRevocation();
                  resetResend();
                  setSelectedInvitationId(invitation.id);
                }}
              />
            ))}
          </section>

          {selectedAccount && (
            <PanelPopup
              key={selectedAccount.id}
              isOpen
              aria-label={`Konto ${selectedAccount.email}`}
              onOpenChange={(open) => {
                if (!open) setSelectedAccountId(undefined);
              }}
            >
              <PanelPopup.Body className="pb-6 pt-12">
                <PanelDetailHeader
                  eyebrow="Konto"
                  status={{
                    isEnabled: selectedAccount.status === "ENABLED",
                    label:
                      selectedAccount.status === "ENABLED"
                        ? "Włączone"
                        : "Wyłączone",
                  }}
                  title={selectedAccount.email}
                  trailingActions={
                    <>
                      {selectedAccount.status === "ENABLED" ? (
                        <HoldToConfirmButton
                          isDisabled={isChangingStatus || isDeleting}
                          isPending={isChangingStatus}
                          onConfirm={() => changeStatus(selectedAccount)}
                        >
                          <DisableIcon size={18} /> Przytrzymaj, aby wyłączyć
                        </HoldToConfirmButton>
                      ) : (
                        <Button
                          isDisabled={isDeleting}
                          isPending={isChangingStatus}
                          variant="secondary"
                          onPress={() => void changeStatus(selectedAccount)}
                        >
                          <CheckIcon size={18} /> Włącz
                        </Button>
                      )}
                      <HoldToConfirmButton
                        isDisabled={isDeleting || isChangingStatus}
                        isPending={isDeleting}
                        onConfirm={() => removeAccount(selectedAccount)}
                      >
                        <DeleteIcon size={18} /> Przytrzymaj, aby usunąć
                      </HoldToConfirmButton>
                    </>
                  }
                />
                <p className="mt-4 text-sm text-muted">
                  Wyłączenie wyloguje konto i unieważni zaproszenia. Usunięcie
                  trwale odbierze dostęp.
                </p>
                {deletionError && (
                  <Alert status="danger">
                    <Alert.Content>
                      <Alert.Title>Nie udało się usunąć konta</Alert.Title>
                      <Alert.Description>
                        {getErrorMessage(deletionError)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
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
            {createdInvitation
              ? "Zaproszenie wysłane do kolejki"
              : "Nowe konto"}
          </PanelPopup.Heading>
        </PanelPopup.Header>
        {createdInvitation ? (
          <PanelPopup.Body className="min-w-0 max-w-full pb-6">
            <p className="break-words">
              Wiadomość z zaproszeniem do lokalu{" "}
              {createdInvitation.locationName} dla {createdInvitation.email}{" "}
              oczekuje na wysłanie. Stan wysyłki znajdziesz w historii
              zaproszeń.
            </p>
            <Button
              className="mt-4"
              onPress={() => {
                setCreatedInvitation(undefined);
                setIsCreateOpen(false);
              }}
            >
              Gotowe
            </Button>
          </PanelPopup.Body>
        ) : (
          <form className="min-w-0 max-w-full" onSubmit={submitInvitation}>
            <PanelPopup.Body className="flex min-w-0 max-w-full flex-col gap-4">
              {creationError && !replacementInput && (
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
                isDisabled={isCreating || !!replacementInput}
                label="E-mail zapraszanej osoby"
                maxLength={200}
                name="invitation-email"
                value={email}
                onChange={(value) => {
                  setEmail(value);
                  setEmailError(undefined);
                }}
              />
              {replacementInput ? (
                <p>
                  Lokal:{" "}
                  {
                    locations.find(
                      (location) => location.id === replacementInput.locationId,
                    )?.name
                  }{" "}
                  ·{" "}
                  {replacementInput.role === "MANAGER"
                    ? "Kierownik"
                    : "Pracownik"}
                </p>
              ) : isAdministrator ? (
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
              {isAdministrator && !replacementInput && (
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
              <Button aria-label="Anuluj" slot="close" variant="tertiary">
                Anuluj
              </Button>
              {replacementInput ? (
                <div className="flex flex-col gap-3">
                  <p role="alert">
                    Dla tego adresu istnieje oczekujące zaproszenie. Zastąpienie
                    unieważni poprzedni link i wyśle nową wiadomość.
                  </p>
                  <HoldToConfirmButton
                    isDisabled={isCreating}
                    isPending={isCreating}
                    onConfirm={() =>
                      issueInvitation({
                        ...replacementInput,
                        replaceExisting: true,
                      })
                    }
                  >
                    Przytrzymaj, aby zastąpić zaproszenie
                  </HoldToConfirmButton>
                  <Button
                    variant="tertiary"
                    onPress={() => {
                      setReplacementInput(undefined);
                      resetCreation();
                    }}
                  >
                    Wróć do formularza
                  </Button>
                </div>
              ) : (
                <Button isPending={isCreating} type="submit">
                  <PlusIcon size={18} />
                  {isCreating ? "Tworzenie…" : "Utwórz zaproszenie"}
                </Button>
              )}
            </PanelPopup.Footer>
          </form>
        )}
      </PanelPopup>

      {selectedInvitation && (
        <PanelPopup
          key={selectedInvitation.id}
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
                selectedInvitation.state === "PENDING" &&
                new Date(selectedInvitation.expiresAt).getTime() > now ? (
                  <>
                    <HoldToConfirmButton
                      isDisabled={isRevoking || isResending}
                      isPending={isRevoking}
                      onConfirm={() => revokeInvitation(selectedInvitation)}
                    >
                      <DisableIcon size={18} /> Przytrzymaj, aby unieważnić
                    </HoldToConfirmButton>
                    <HoldToConfirmButton
                      isDisabled={isRevoking || isResending}
                      isPending={isResending}
                      onConfirm={() => resendInvitation(selectedInvitation)}
                    >
                      <ArrowsClockwiseIcon size={18} /> Przytrzymaj, aby wysłać
                      ponownie
                    </HoldToConfirmButton>
                  </>
                ) : undefined
              }
            />
            {selectedInvitation.state === "PENDING" && (
              <p className="mt-3 text-sm text-muted">
                Unieważnienie lub ponowna wysyłka natychmiast unieważni
                poprzedni link. Ponowna wysyłka utworzy nowe zaproszenie i wyśle
                e-mail.
              </p>
            )}
            {resendError && (
              <Alert status="danger">
                <Alert.Content>
                  <Alert.Title>Nie udało się wysłać ponownie</Alert.Title>
                  <Alert.Description>
                    {getErrorMessage(resendError)}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}

            {revocationError && (
              <Alert status="danger">
                <Alert.Content>
                  <Alert.Title>
                    Nie udało się unieważnić zaproszenia
                  </Alert.Title>
                  <Alert.Description>
                    {getErrorMessage(revocationError)}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}
            <dl className="mt-6 grid gap-5 border-t border-separator pt-6 sm:grid-cols-2">
              <div>
                <dt className="text-sm text-muted">Status</dt>
                <dd className="mt-1 font-medium">
                  {invitationStates[selectedInvitation.state]}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted">Wysyłka</dt>
                <dd className="mt-1 font-medium">
                  {deliveryStates[selectedInvitation.deliveryState]}
                </dd>
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
    </div>
  );
}

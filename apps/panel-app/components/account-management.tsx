"use client";

import type { FormEvent } from "react";
import type { TenantAccount } from "@/src/api/authentication";

import {
  Alert,
  AlertDialog,
  Badge,
  Button,
  ListBox,
  Modal,
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
  UserRoundPlus as InvitationIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";

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

  return "Account management could not be completed. Check your connection and try again.";
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
      label="Location"
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
  return new Intl.DateTimeFormat(undefined, {
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
  const [isInvitationsOpen, setIsInvitationsOpen] = useState(false);
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
  const selectedAccount =
    accounts.find((candidate) => candidate.id === selectedAccountId) ??
    accounts[0];
  const locationNames = new Map(
    locations.map((location) => [location.id, location.name]),
  );
  const pendingInvitations = invitations.filter(
    (invitation) => new Date(invitation.expiresAt).getTime() > now,
  );
  const collectionError = locationsError ?? accountsError ?? statusError;

  async function submitInvitation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!locationId || isCreating) return;
    resetCreation();
    const created = await triggerCreation({
      locationId,
      role: selectedRole,
    });

    if (!created) return;
    setCreatedInvitation(created);
    await mutateInvitations((current) => [created, ...(current ?? [])], {
      revalidate: false,
    });
    void mutateInvitations(undefined, { throwOnError: false });
  }

  async function revokeInvitation(): Promise<void> {
    if (!invitationToRevoke) return;
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
    void mutateInvitations(undefined, { throwOnError: false });
  }

  async function changeStatus(
    managedAccount: ManagedAccount,
  ): Promise<boolean> {
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
    if (!accountToDelete) return false;

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
    resetCreation();
    setCreatedInvitation(undefined);
    setSelectedLocationId(locationId ?? enabledLocations[0]?.id);
    setRole("OPERATOR");
    setIsCreateOpen(true);
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-4">
          <h1 className="page-title">Accounts</h1>
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                aria-label="New account"
                className="rounded-md"
                isDisabled={enabledLocations.length === 0}
                size="lg"
                onPress={openCreate}
              >
                <PlusIcon size={20} />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>New account</Tooltip.Content>
          </Tooltip>
        </div>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Badge.Anchor>
              <Button
                isIconOnly
                aria-label={
                  pendingInvitations.length > 0
                    ? `Invitations, ${pendingInvitations.length} pending`
                    : "Invitations"
                }
                className="rounded-md"
                size="lg"
                variant="secondary"
                onPress={() => setIsInvitationsOpen(true)}
              >
                <InvitationIcon size={20} />
              </Button>
              {pendingInvitations.length > 0 && (
                <Badge color="accent" size="sm">
                  {pendingInvitations.length}
                </Badge>
              )}
            </Badge.Anchor>
          </Tooltip.Trigger>
          <Tooltip.Content>Invitations</Tooltip.Content>
        </Tooltip>
      </div>

      {collectionError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Account request failed</Alert.Title>
            <Alert.Description>
              {getErrorMessage(collectionError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {!areLocationsLoading && enabledLocations.length === 0 && (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Account invitations unavailable</Alert.Title>
            <Alert.Description>
              Create a location before inviting an account.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {areLocationsLoading || areAccountsLoading ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Loading accounts" />
        </div>
      ) : accounts.length === 0 ? (
        <div className="py-12">
          <h2 className="section-title">No managed accounts yet</h2>
          <p className="mt-2 max-w-sm secondary-text">
            {isAdministrator
              ? "Invite a manager or operator to an existing location."
              : "Invite an operator to your location."}
          </p>
        </div>
      ) : (
        <div className="grid gap-6 md:grid-cols-[minmax(220px,0.65fr)_minmax(0,1.35fr)]">
          <section className="border-t border-separator pt-2 md:border-r md:border-t-0 md:pr-6 md:pt-0">
            {accounts.map((managedAccount) => (
              <PanelCard
                key={managedAccount.id}
                accessibilityLabel={`View account ${managedAccount.email}`}
                isSelected={managedAccount.id === selectedAccount?.id}
                metadata={
                  <span
                    className={
                      managedAccount.status === "ENABLED"
                        ? "text-accent"
                        : "secondary-text"
                    }
                  >
                    {managedAccount.status === "ENABLED"
                      ? "Enabled"
                      : "Disabled"}
                  </span>
                }
                title={managedAccount.email}
                trailing={<ArrowRightIcon size={17} />}
                onPress={() => setSelectedAccountId(managedAccount.id)}
              />
            ))}
          </section>

          {selectedAccount && (
            <section className="min-w-0">
              <PanelDetailHeader
                eyebrow="Account"
                title={selectedAccount.email}
                trailingActions={
                  <>
                    <Tooltip delay={500}>
                      <Tooltip.Trigger>
                        <Button
                          isIconOnly
                          aria-label={`${selectedAccount.status === "ENABLED" ? "Disable" : "Enable"} account ${selectedAccount.email}`}
                          className="shrink-0 rounded-md"
                          isPending={isChangingStatus}
                          variant={
                            selectedAccount.status === "ENABLED"
                              ? "danger"
                              : "secondary"
                          }
                          onPress={() => {
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
                          ? "Disable"
                          : "Enable"}
                      </Tooltip.Content>
                    </Tooltip>
                    <Tooltip delay={500}>
                      <Tooltip.Trigger>
                        <Button
                          isIconOnly
                          aria-label={`Delete account ${selectedAccount.email}`}
                          className="shrink-0 rounded-md"
                          variant="danger"
                          onPress={() => {
                            resetDeletion();
                            setDeleteConfirmation("");
                            setAccountToDelete(selectedAccount);
                          }}
                        >
                          <DeleteIcon size={20} />
                        </Button>
                      </Tooltip.Trigger>
                      <Tooltip.Content>Delete</Tooltip.Content>
                    </Tooltip>
                  </>
                }
              />

              <dl className="mt-8 grid gap-5 border-t border-separator pt-6 sm:grid-cols-2">
                <div>
                  <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                    Role
                  </dt>
                  <dd className="mt-1 font-medium">
                    {selectedAccount.role === "MANAGER"
                      ? "Manager"
                      : "Operator"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                    Location
                  </dt>
                  <dd className="mt-1 font-medium">
                    {locationNames.get(selectedAccount.locationId) ??
                      "Unavailable"}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                    Email
                  </dt>
                  <dd className="mt-1 break-words font-medium">
                    {selectedAccount.email}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
                    Created
                  </dt>
                  <dd className="mt-1 font-medium">
                    {new Date(selectedAccount.createdAt).toLocaleDateString()}
                  </dd>
                </div>
              </dl>
            </section>
          )}
        </div>
      )}

      <Modal
        isOpen={isCreateOpen}
        onOpenChange={(open) => {
          setIsCreateOpen(open);
          if (!open) setCreatedInvitation(undefined);
        }}
      >
        <Modal.Backdrop>
          <Modal.Container placement="center" size="lg">
            <Modal.Dialog className="min-w-0">
              <Modal.CloseTrigger />
              <Modal.Header>
                <Modal.Heading>
                  {createdInvitation ? "Invitation link" : "New account"}
                </Modal.Heading>
              </Modal.Header>
              {createdInvitation ? (
                <Modal.Body className="min-w-0 max-w-full pb-6">
                  <OneTimeSecret
                    secret={{
                      title: "Share this invitation",
                      description: `${createdInvitation.role === "MANAGER" ? "Manager" : "Operator"} · ${createdInvitation.locationName}`,
                      value: createdInvitation.invitationLink,
                    }}
                    onConfirmed={() => setIsCreateOpen(false)}
                  />
                </Modal.Body>
              ) : (
                <form
                  className="min-w-0 max-w-full"
                  onSubmit={submitInvitation}
                >
                  <Modal.Body className="flex min-w-0 max-w-full flex-col gap-4">
                    {creationError && (
                      <Alert status="danger">
                        <Alert.Indicator />
                        <Alert.Content>
                          <Alert.Title>
                            Invitation could not be created
                          </Alert.Title>
                          <Alert.Description>
                            {getErrorMessage(creationError)}
                          </Alert.Description>
                        </Alert.Content>
                      </Alert>
                    )}
                    {isAdministrator ? (
                      <LocationSelect
                        locations={enabledLocations}
                        selectedId={locationId}
                        onChange={setSelectedLocationId}
                      />
                    ) : (
                      <div className="min-w-0 max-w-full">
                        <p className="text-sm font-medium">Location</p>
                        <p className="mt-1 break-words text-sm text-muted">
                          {
                            locations.find(
                              (location) => location.id === locationId,
                            )?.name
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
                        <Label>Role</Label>
                        <Radio value="OPERATOR">
                          <Radio.Content>
                            <Radio.Control>
                              <Radio.Indicator />
                            </Radio.Control>
                            Operator
                          </Radio.Content>
                        </Radio>
                        <Radio value="MANAGER">
                          <Radio.Content>
                            <Radio.Control>
                              <Radio.Indicator />
                            </Radio.Control>
                            Manager
                          </Radio.Content>
                        </Radio>
                      </RadioGroup>
                    )}
                  </Modal.Body>
                  <Modal.Footer className="max-w-full flex-wrap">
                    <Button slot="close" variant="tertiary">
                      Cancel
                    </Button>
                    <Button isPending={isCreating} type="submit">
                      <PlusIcon size={18} />
                      {isCreating ? "Creating…" : "Create invitation"}
                    </Button>
                  </Modal.Footer>
                </form>
              )}
            </Modal.Dialog>
          </Modal.Container>
        </Modal.Backdrop>
      </Modal>

      <Modal isOpen={isInvitationsOpen} onOpenChange={setIsInvitationsOpen}>
        <Modal.Backdrop>
          <Modal.Container placement="center" size="lg">
            <Modal.Dialog className="max-h-[min(90vh,760px)] sm:max-w-3xl">
              <Modal.CloseTrigger />
              <Modal.Header>
                <Modal.Heading>Pending invitations</Modal.Heading>
              </Modal.Header>
              <Modal.Body className="pb-6">
                {areInvitationsLoading ? (
                  <div className="flex min-h-48 items-center justify-center">
                    <Spinner aria-label="Loading invitations" />
                  </div>
                ) : invitationsError ? (
                  <Alert status="danger">
                    <Alert.Indicator />
                    <Alert.Content>
                      <Alert.Title>Invitations could not load</Alert.Title>
                      <Alert.Description>
                        {getErrorMessage(invitationsError)}
                      </Alert.Description>
                      <Button
                        className="mt-3"
                        size="sm"
                        variant="danger"
                        onPress={() => void mutateInvitations()}
                      >
                        Retry
                      </Button>
                    </Alert.Content>
                  </Alert>
                ) : pendingInvitations.length === 0 ? (
                  <p className="py-12 text-center secondary-text">
                    No pending invitations
                  </p>
                ) : (
                  <ul className="divide-y divide-separator">
                    {pendingInvitations.map((invitation) => (
                      <li
                        key={invitation.id}
                        className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between"
                      >
                        <div className="min-w-0">
                          <p className="font-semibold">
                            {invitation.role === "MANAGER"
                              ? "Manager"
                              : "Operator"}
                            <span className="font-normal text-muted">
                              {" "}
                              · {invitation.locationName}
                            </span>
                          </p>
                          <p className="mt-1 text-sm text-muted">
                            Created by {invitation.issuedByEmail} on{" "}
                            {formatDateTime(invitation.createdAt)}
                          </p>
                          <p className="mt-1 text-sm text-muted">
                            Expires {formatDateTime(invitation.expiresAt)}
                          </p>
                        </div>
                        <Tooltip delay={500}>
                          <Tooltip.Trigger>
                            <Button
                              isIconOnly
                              aria-label={`Revoke ${invitation.role === "MANAGER" ? "manager" : "operator"} invitation for ${invitation.locationName}`}
                              className="shrink-0 self-end rounded-md sm:self-auto"
                              variant="danger"
                              onPress={() => {
                                resetRevocation();
                                setInvitationToRevoke(invitation);
                              }}
                            >
                              <DisableIcon size={20} />
                            </Button>
                          </Tooltip.Trigger>
                          <Tooltip.Content>Revoke</Tooltip.Content>
                        </Tooltip>
                      </li>
                    ))}
                  </ul>
                )}
              </Modal.Body>
            </Modal.Dialog>
          </Modal.Container>
        </Modal.Backdrop>
      </Modal>

      <AlertDialog
        isOpen={Boolean(invitationToRevoke)}
        onOpenChange={(open) => {
          if (!open && !isRevoking) setInvitationToRevoke(undefined);
        }}
      >
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[440px]">
              <AlertDialog.CloseTrigger />
              <AlertDialog.Header>
                <AlertDialog.Icon status="danger" />
                <AlertDialog.Heading>Revoke invitation?</AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body className="flex flex-col gap-4">
                <p>
                  The{" "}
                  {invitationToRevoke?.role === "MANAGER"
                    ? "manager"
                    : "operator"}{" "}
                  invitation for {invitationToRevoke?.locationName} will stop
                  working immediately.
                </p>
                {revocationError && (
                  <Alert status="danger">
                    <Alert.Indicator />
                    <Alert.Content>
                      <Alert.Title>Invitation could not be revoked</Alert.Title>
                      <Alert.Description>
                        {getErrorMessage(revocationError)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button isDisabled={isRevoking} slot="close" variant="tertiary">
                  Cancel
                </Button>
                <Button
                  isPending={isRevoking}
                  variant="danger"
                  onPress={() => void revokeInvitation()}
                >
                  Revoke
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>

      <AlertDialog
        isOpen={Boolean(accountToDisable)}
        onOpenChange={(open) => {
          if (!open) setAccountToDisable(undefined);
        }}
      >
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[420px]">
              <AlertDialog.CloseTrigger />
              <AlertDialog.Header>
                <AlertDialog.Icon status="warning" />
                <AlertDialog.Heading>
                  Disable {accountToDisable?.email}?
                </AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body>
                This account will be signed out, its pending invitations will be
                revoked, and it cannot access the panel until enabled again.
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button slot="close" variant="tertiary">
                  Cancel
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
                  Disable
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>

      <AlertDialog
        isOpen={Boolean(accountToDelete)}
        onOpenChange={(open) => {
          if (!open && !isDeleting) {
            setAccountToDelete(undefined);
            setDeleteConfirmation("");
            resetDeletion();
          }
        }}
      >
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[460px]">
              <AlertDialog.CloseTrigger />
              <AlertDialog.Header>
                <AlertDialog.Icon status="danger" />
                <AlertDialog.Heading>
                  Delete {accountToDelete?.email}?
                </AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body className="flex flex-col gap-4">
                <p>
                  This account will be signed out, disappear from Accounts, lose
                  its pending invitations, and never regain access. Type its
                  exact email to confirm.
                </p>
                <FormTextField
                  fullWidth
                  isRequired
                  inputProps={{ autoComplete: "off" }}
                  isDisabled={isDeleting}
                  label={`Type ${accountToDelete?.email} to confirm`}
                  name="delete-account-confirmation"
                  value={deleteConfirmation}
                  onChange={setDeleteConfirmation}
                />
                {deletionError && (
                  <Alert status="danger">
                    <Alert.Indicator />
                    <Alert.Content>
                      <Alert.Title>Account could not be deleted</Alert.Title>
                      <Alert.Description>
                        {getErrorMessage(deletionError)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button isDisabled={isDeleting} slot="close" variant="tertiary">
                  Cancel
                </Button>
                <Button
                  isDisabled={deleteConfirmation !== accountToDelete?.email}
                  isPending={isDeleting}
                  variant="danger"
                  onPress={() => void removeAccount()}
                >
                  Delete
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
    </div>
  );
}

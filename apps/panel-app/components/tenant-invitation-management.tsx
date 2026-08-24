"use client";

import type { FormEvent } from "react";

import {
  Alert,
  AlertDialog,
  Button,
  Input,
  Label,
  Modal,
  Spinner,
  TextField,
  Tooltip,
} from "@heroui/react";
import { Ban as RevokeIcon, Plus as PlusIcon } from "lucide-react";
import { useEffect, useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { OneTimeSecret } from "@/components/integrations/one-time-secret";
import { PanelCard } from "@/components/panel-card";
import { ApiError } from "@/src/api/api-fetch";
import { staffTenantRegistrationInvitationsKey } from "@/src/api/cache-keys";
import {
  createTenantRegistrationInvitation,
  listTenantRegistrationInvitations,
  revokeTenantRegistrationInvitation,
  type CreatedTenantRegistrationInvitation,
  type CreateTenantRegistrationInvitationInput,
  type TenantRegistrationInvitation,
} from "@/src/api/tenant-registration-invitations";

function createMutation(
  _key: ReturnType<typeof staffTenantRegistrationInvitationsKey>,
  { arg }: { arg: CreateTenantRegistrationInvitationInput },
): Promise<CreatedTenantRegistrationInvitation> {
  return createTenantRegistrationInvitation(arg);
}

function revokeMutation(
  _key: ReturnType<typeof staffTenantRegistrationInvitationsKey>,
  { arg }: { arg: string },
): Promise<boolean> {
  return revokeTenantRegistrationInvitation(arg);
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

function errorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Check the submitted label.";
  }
  if (error instanceof ApiError) return error.message;

  return "Tenant invitations could not be updated. Check your connection and try again.";
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function TenantInvitationManagement({
  accountId,
}: {
  accountId: string;
}) {
  const invitationKey = staffTenantRegistrationInvitationsKey(accountId);
  const [now, setNow] = useState(() => Date.now());
  const [label, setLabel] = useState("");
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createdInvitation, setCreatedInvitation] =
    useState<CreatedTenantRegistrationInvitation>();
  const [invitationToRevoke, setInvitationToRevoke] =
    useState<TenantRegistrationInvitation>();

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 60_000);

    return () => window.clearInterval(interval);
  }, []);

  const {
    data: invitations = [],
    error: collectionError,
    isLoading,
    mutate: mutateInvitations,
  } = useSWR(invitationKey, listTenantRegistrationInvitations, {
    errorRetryCount: 3,
    shouldRetryOnError,
  });
  const {
    error: creationError,
    isMutating: isCreating,
    reset: resetCreation,
    trigger: triggerCreation,
  } = useSWRMutation(invitationKey, createMutation, { throwOnError: false });
  const {
    error: revocationError,
    isMutating: isRevoking,
    reset: resetRevocation,
    trigger: triggerRevocation,
  } = useSWRMutation(invitationKey, revokeMutation, { throwOnError: false });

  const pendingInvitations = invitations.filter(
    (invitation) => new Date(invitation.expiresAt).getTime() > now,
  );

  function openCreate(): void {
    setLabel("");
    setCreatedInvitation(undefined);
    resetCreation();
    setIsCreateOpen(true);
  }

  async function submitInvitation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isCreating) return;
    resetCreation();
    const created = await triggerCreation({ label });

    if (!created) return;
    setCreatedInvitation(created);
    const secretFreeInvitation: TenantRegistrationInvitation = {
      id: created.id,
      label: created.label,
      issuedByUsername: created.issuedByUsername,
      createdAt: created.createdAt,
      expiresAt: created.expiresAt,
    };

    await mutateInvitations(
      (current) => [secretFreeInvitation, ...(current ?? [])],
      { revalidate: false },
    );
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

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="page-title">Tenant invitations</h1>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label="Create tenant invitation"
              className="rounded-md"
              size="lg"
              onPress={openCreate}
            >
              <PlusIcon size={20} />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content>Create invitation</Tooltip.Content>
        </Tooltip>
      </div>

      {isLoading ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Loading tenant invitations" />
        </div>
      ) : collectionError ? (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Tenant invitations could not load</Alert.Title>
            <Alert.Description>
              {errorMessage(collectionError)}
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
        <div className="py-12">
          <h2 className="section-title">No pending invitations</h2>
          <p className="mt-2 max-w-lg secondary-text">
            Create a one-time link when a restaurant is ready to join Kairos.
          </p>
        </div>
      ) : (
        <section aria-label="Pending tenant invitations" className="grid gap-2">
          {pendingInvitations.map((invitation) => (
            <PanelCard
              key={invitation.id}
              metadata={
                <div className="flex flex-col gap-1 text-muted sm:flex-row sm:flex-wrap sm:gap-x-4">
                  <span>Created by {invitation.issuedByUsername}</span>
                  <span>Created {formatDateTime(invitation.createdAt)}</span>
                  <span>Expires {formatDateTime(invitation.expiresAt)}</span>
                </div>
              }
              title={invitation.label}
              trailing={
                <Tooltip delay={500}>
                  <Tooltip.Trigger>
                    <Button
                      isIconOnly
                      aria-label={`Revoke tenant invitation ${invitation.label}`}
                      className="shrink-0 rounded-md"
                      variant="danger"
                      onPress={() => {
                        resetRevocation();
                        setInvitationToRevoke(invitation);
                      }}
                    >
                      <RevokeIcon size={20} />
                    </Button>
                  </Tooltip.Trigger>
                  <Tooltip.Content>Revoke</Tooltip.Content>
                </Tooltip>
              }
            />
          ))}
        </section>
      )}

      <Modal
        isOpen={isCreateOpen}
        onOpenChange={(open) => {
          if (!open && createdInvitation) return;
          setIsCreateOpen(open);
          if (!open) setCreatedInvitation(undefined);
        }}
      >
        <Modal.Backdrop isDismissable={!createdInvitation}>
          <Modal.Container placement="center" size="lg">
            <Modal.Dialog className="min-w-0">
              {!createdInvitation && <Modal.CloseTrigger />}
              <Modal.Header>
                <Modal.Heading>
                  {createdInvitation
                    ? "Tenant invitation link"
                    : "New tenant invitation"}
                </Modal.Heading>
              </Modal.Header>
              {createdInvitation ? (
                <Modal.Body className="min-w-0 max-w-full pb-6">
                  <OneTimeSecret
                    secret={{
                      title: "Share this invitation",
                      description: `${createdInvitation.label} · Expires ${formatDateTime(createdInvitation.expiresAt)}`,
                      value: createdInvitation.invitationLink,
                    }}
                    onConfirmed={() => {
                      setCreatedInvitation(undefined);
                      setIsCreateOpen(false);
                    }}
                  />
                </Modal.Body>
              ) : (
                <form onSubmit={submitInvitation}>
                  <Modal.Body className="flex flex-col gap-4">
                    {creationError && (
                      <Alert status="danger">
                        <Alert.Indicator />
                        <Alert.Content>
                          <Alert.Title>
                            Invitation could not be created
                          </Alert.Title>
                          <Alert.Description>
                            {errorMessage(creationError)}
                          </Alert.Description>
                        </Alert.Content>
                      </Alert>
                    )}
                    <TextField
                      fullWidth
                      isRequired
                      isDisabled={isCreating}
                      maxLength={120}
                      name="label"
                      value={label}
                      onChange={setLabel}
                    >
                      <Label>Restaurant label</Label>
                      <Input autoComplete="off" />
                    </TextField>
                    <p className="text-sm text-muted">
                      This internal label is visible to platform operators. It
                      is not included in the registration link.
                    </p>
                  </Modal.Body>
                  <Modal.Footer>
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
                  The invitation for {invitationToRevoke?.label} will stop
                  working immediately.
                </p>
                {revocationError && (
                  <Alert status="danger">
                    <Alert.Indicator />
                    <Alert.Content>
                      <Alert.Title>Invitation could not be revoked</Alert.Title>
                      <Alert.Description>
                        {errorMessage(revocationError)}
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
    </div>
  );
}

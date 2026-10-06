import type { FormEvent } from "react";
import type { Location } from "@/src/api/locations";
import type { PendingOneTimeSecret } from "./one-time-secret";
import type {
  WebhookEventType,
  WebhookSubscription,
  WebhookSubscriptionInput,
} from "@/src/api/webhook-subscriptions";

import { Alert, AlertDialog, Button, Spinner, Tooltip } from "@heroui/react";
import {
  Ban as DisableIcon,
  Check as CheckIcon,
  Pencil as EditIcon,
  Trash2 as TrashIcon,
} from "lucide-react";
import { useState } from "react";
import useSWR from "swr";

import { FormTextField } from "@/components/form-controls";
import {
  formatIntegrationDateTime,
  getIntegrationErrorMessage,
  shouldRetryIntegrationRequest,
} from "@/components/integrations/integration-ui";
import { staffWebhookSubscriptionsKey } from "@/src/api/cache-keys";
import {
  archiveWebhookSubscription,
  createWebhookSubscription,
  listWebhookSubscriptions,
  retireWebhookSigningSecret,
  rotateWebhookSigningSecret,
  updateWebhookSubscription,
  updateWebhookSubscriptionStatus,
} from "@/src/api/webhook-subscriptions";

const webhookEventTypes = [
  "order.created",
  "order.ready",
  "order.completed",
  "order.canceled",
] as const satisfies readonly WebhookEventType[];

const webhookEventLabels: Record<WebhookEventType, string> = {
  "order.created": "Order created",
  "order.ready": "Order ready",
  "order.completed": "Order completed",
  "order.canceled": "Order canceled",
};

type SubscriptionDraft = {
  name: string;
  destinationUrl: string;
  locationIds: string[];
  eventTypes: WebhookEventType[];
};

type WebhookConfirmation =
  | {
      kind: "disable";
      subscription: WebhookSubscription;
    }
  | {
      kind: "delete";
      subscription: WebhookSubscription;
    }
  | {
      kind: "retire";
      subscription: WebhookSubscription;
      versionId: string;
    };

function replaceSubscription(
  subscriptions: WebhookSubscription[] | undefined,
  updated: WebhookSubscription,
): WebhookSubscription[] {
  return (subscriptions ?? [updated]).map((subscription) =>
    subscription.id === updated.id ? updated : subscription,
  );
}

function toggleValue<T extends string>(values: T[], value: T): T[] {
  return values.includes(value)
    ? values.filter((item) => item !== value)
    : [...values, value];
}

function SubscriptionFields({
  draft,
  isDisabled,
  locations,
  onChange,
}: {
  draft: SubscriptionDraft;
  isDisabled: boolean;
  locations: Location[];
  onChange: (draft: SubscriptionDraft) => void;
}) {
  return (
    <>
      <FormTextField
        fullWidth
        isRequired
        isDisabled={isDisabled}
        label="Webhook name"
        maxLength={64}
        name="webhook-name"
        value={draft.name}
        onChange={(name) => onChange({ ...draft, name })}
      />

      <FormTextField
        fullWidth
        isRequired
        inputProps={{
          autoCapitalize: "none",
          autoComplete: "off",
          spellCheck: false,
        }}
        isDisabled={isDisabled}
        label="Destination URL"
        maxLength={2048}
        name="webhook-destination"
        placeholder="Destination URL (https://…)"
        type="url"
        value={draft.destinationUrl}
        onChange={(destinationUrl) => onChange({ ...draft, destinationUrl })}
      />

      <div className="flex flex-col gap-3">
        <p className="text-sm font-medium">Locations</p>
        <div className="flex flex-wrap gap-3">
          {locations.map((location) => (
            <Button
              key={location.id}
              aria-pressed={draft.locationIds.includes(location.id)}
              isDisabled={isDisabled}
              variant={
                draft.locationIds.includes(location.id)
                  ? "primary"
                  : "secondary"
              }
              onPress={() =>
                onChange({
                  ...draft,
                  locationIds: toggleValue(draft.locationIds, location.id),
                })
              }
            >
              {location.name}
            </Button>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-3">
        <p className="text-sm font-medium">Events</p>
        <div className="flex flex-wrap gap-3">
          {webhookEventTypes.map((eventType) => (
            <Button
              key={eventType}
              aria-pressed={draft.eventTypes.includes(eventType)}
              isDisabled={isDisabled}
              variant={
                draft.eventTypes.includes(eventType) ? "primary" : "secondary"
              }
              onPress={() =>
                onChange({
                  ...draft,
                  eventTypes: toggleValue(draft.eventTypes, eventType),
                })
              }
            >
              {webhookEventLabels[eventType]}
            </Button>
          ))}
        </div>
      </div>
    </>
  );
}

function WebhookEditor({
  isPending,
  locations,
  subscription,
  onCancel,
  onSave,
}: {
  isPending: boolean;
  locations: Location[];
  subscription: WebhookSubscription;
  onCancel: () => void;
  onSave: (input: WebhookSubscriptionInput) => Promise<void>;
}) {
  const [draft, setDraft] = useState<SubscriptionDraft>({
    name: subscription.name,
    destinationUrl: subscription.destinationUrl,
    locationIds: subscription.locationIds,
    eventTypes: subscription.eventTypes,
  });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSave(draft);
  }

  return (
    <section className="flex flex-col gap-4 border-y border-separator py-5">
      <div>
        <h4 className="font-semibold">Edit {subscription.name}</h4>
        <p className="text-sm text-muted">
          Changes apply only to deliveries created after this update.
        </p>
      </div>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <SubscriptionFields
          draft={draft}
          isDisabled={isPending}
          locations={locations}
          onChange={setDraft}
        />
        <div className="flex flex-wrap gap-3">
          <Button isPending={isPending} type="submit">
            {isPending ? "Saving…" : "Save"}
          </Button>
          <Button isDisabled={isPending} variant="secondary" onPress={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </section>
  );
}

export function WebhookSubscriptionManagement({
  accountId,
  integrationId,
  locations,
  onSecretIssued,
}: {
  accountId: string;
  integrationId: string;
  locations: Location[];
  onSecretIssued: (secret: PendingOneTimeSecret) => void;
}) {
  const enabledLocations = locations.filter(
    (location) => location.status === "ENABLED",
  );
  const [draft, setDraft] = useState<SubscriptionDraft>({
    name: "",
    destinationUrl: "",
    locationIds: [],
    eventTypes: [...webhookEventTypes],
  });
  const [editedSubscriptionId, setEditedSubscriptionId] = useState<string>();
  const [confirmation, setConfirmation] = useState<WebhookConfirmation>();
  const [pendingAction, setPendingAction] = useState<string>();
  const [actionError, setActionError] = useState<unknown>();

  const {
    data: subscriptions = [],
    error: subscriptionsError,
    isLoading: areSubscriptionsLoading,
    mutate: mutateSubscriptions,
  } = useSWR(
    staffWebhookSubscriptionsKey(accountId, integrationId),
    () => listWebhookSubscriptions(integrationId),
    {
      errorRetryCount: 3,
      shouldRetryOnError: shouldRetryIntegrationRequest,
    },
  );

  const editedSubscription = subscriptions.find(
    (subscription) => subscription.id === editedSubscriptionId,
  );
  const error = subscriptionsError ?? actionError;

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction("create");

    try {
      const result = await createWebhookSubscription(integrationId, draft);

      onSecretIssued({
        title: `Signing secret for ${result.subscription.name}`,
        description:
          "Configure this signing secret at the webhook recipient, then return to enable the subscription.",
        value: result.signingSecret,
      });
      await mutateSubscriptions(
        (current) => [
          result.subscription,
          ...(current ?? []).filter(
            (subscription) => subscription.id !== result.subscription.id,
          ),
        ],
        { revalidate: false },
      );
      setDraft({
        name: "",
        destinationUrl: "",
        locationIds: [],
        eventTypes: [...webhookEventTypes],
      });
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function save(
    subscription: WebhookSubscription,
    input: WebhookSubscriptionInput,
  ) {
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction(`save-${subscription.id}`);

    try {
      const updated = await updateWebhookSubscription(subscription.id, input);

      await mutateSubscriptions(
        (current) => replaceSubscription(current, updated),
        { revalidate: false },
      );
      setEditedSubscriptionId(undefined);
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function changeStatus(
    subscription: WebhookSubscription,
    status: "DISABLED" | "ENABLED",
  ): Promise<boolean> {
    if (pendingAction) return false;

    setActionError(undefined);
    setPendingAction(`status-${subscription.id}`);

    try {
      const updated = await updateWebhookSubscriptionStatus(
        subscription.id,
        status,
      );

      await mutateSubscriptions(
        (current) => replaceSubscription(current, updated),
        { revalidate: false },
      );

      return true;
    } catch (caught) {
      setActionError(caught);

      return false;
    } finally {
      setPendingAction(undefined);
    }
  }

  async function archive(subscription: WebhookSubscription): Promise<boolean> {
    if (pendingAction) return false;

    setActionError(undefined);
    setPendingAction(`archive-${subscription.id}`);

    try {
      await archiveWebhookSubscription(subscription.id);
      await mutateSubscriptions(
        (current) =>
          (current ?? []).filter((item) => item.id !== subscription.id),
        { revalidate: false },
      );
      if (editedSubscriptionId === subscription.id) {
        setEditedSubscriptionId(undefined);
      }

      return true;
    } catch (caught) {
      setActionError(caught);

      return false;
    } finally {
      setPendingAction(undefined);
    }
  }

  async function rotate(subscription: WebhookSubscription) {
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction(`rotate-${subscription.id}`);

    try {
      const result = await rotateWebhookSigningSecret(subscription.id);
      const updated = {
        ...subscription,
        signingSecretVersions: [
          result.version,
          ...subscription.signingSecretVersions.filter(
            (version) => version.id !== result.version.id,
          ),
        ],
      };

      onSecretIssued({
        title: `New signing secret for ${subscription.name}`,
        description:
          "Update the webhook recipient now. The old signing secret stops working 24 hours after this rotation.",
        value: result.signingSecret,
        afterConfirmed: () => {
          void listWebhookSubscriptions(integrationId)
            .then((freshSubscriptions) =>
              mutateSubscriptions(freshSubscriptions, { revalidate: false }),
            )
            .catch(() => undefined);
        },
      });
      await mutateSubscriptions(
        (current) => replaceSubscription(current, updated),
        { revalidate: false },
      );
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function retire(
    subscription: WebhookSubscription,
    versionId: string,
  ): Promise<boolean> {
    if (pendingAction) return false;

    setActionError(undefined);
    setPendingAction(`retire-${versionId}`);

    try {
      const retired = await retireWebhookSigningSecret(
        subscription.id,
        versionId,
      );
      const updated = {
        ...subscription,
        signingSecretVersions: subscription.signingSecretVersions.map(
          (version) => (version.id === retired.id ? retired : version),
        ),
      };

      await mutateSubscriptions(
        (current) => replaceSubscription(current, updated),
        { revalidate: false },
      );

      return true;
    } catch (caught) {
      setActionError(caught);

      return false;
    } finally {
      setPendingAction(undefined);
    }
  }

  async function confirmDestructiveAction() {
    if (!confirmation) return;

    const completed =
      confirmation.kind === "disable"
        ? await changeStatus(confirmation.subscription, "DISABLED")
        : confirmation.kind === "delete"
          ? await archive(confirmation.subscription)
          : await retire(confirmation.subscription, confirmation.versionId);

    if (completed) setConfirmation(undefined);
  }

  const confirmationTitle = confirmation
    ? confirmation.kind === "disable"
      ? `Disable ${confirmation.subscription.name}?`
      : confirmation.kind === "delete"
        ? `Delete ${confirmation.subscription.name}?`
        : `Retire this secret for ${confirmation.subscription.name}?`
    : "";
  const confirmationDescription = confirmation
    ? confirmation.kind === "disable"
      ? "Webhook deliveries will pause until this subscription is enabled again."
      : confirmation.kind === "delete"
        ? "The webhook will be removed and future deliveries will stop. This cannot be undone."
        : "This secret version will stop validating signatures immediately. Confirm that the recipient no longer uses it."
    : "";
  const confirmationLabel = confirmation
    ? confirmation.kind === "disable"
      ? "Disable"
      : confirmation.kind === "delete"
        ? "Delete"
        : "Retire"
    : "Confirm";
  const isConfirmationPending = confirmation
    ? pendingAction ===
      (confirmation.kind === "disable"
        ? `status-${confirmation.subscription.id}`
        : confirmation.kind === "delete"
          ? `archive-${confirmation.subscription.id}`
          : `retire-${confirmation.versionId}`)
    : false;

  return (
    <div className="flex flex-col gap-6">
      {Boolean(error) && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Webhook request failed</Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(error)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <section className="flex flex-col gap-3">
        <div>
          <h3 className="text-xl font-semibold">New webhook</h3>
          <p className="text-sm text-muted">
            New subscriptions start disabled so the recipient can be configured
            before delivery begins.
          </p>
        </div>
        <form className="flex max-w-3xl flex-col gap-4" onSubmit={create}>
          <SubscriptionFields
            draft={draft}
            isDisabled={Boolean(pendingAction) || enabledLocations.length === 0}
            locations={enabledLocations}
            onChange={setDraft}
          />
          <Button
            className="self-start"
            isDisabled={enabledLocations.length === 0}
            isPending={pendingAction === "create"}
            type="submit"
          >
            {pendingAction === "create" ? "Creating…" : "Create"}
          </Button>
        </form>
      </section>

      <section className="flex flex-col gap-3">
        <h3 className="text-xl font-semibold">Webhooks</h3>

        {areSubscriptionsLoading ? (
          <Spinner aria-label="Loading webhook subscriptions" />
        ) : subscriptions.length === 0 ? (
          <p className="text-muted">No webhooks configured.</p>
        ) : (
          <div className="border-t border-separator">
            {subscriptions.map((subscription) => (
              <article
                key={subscription.id}
                className="flex flex-col gap-3 border-b border-separator py-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <h4 className="font-semibold">{subscription.name}</h4>
                  <span
                    className={
                      subscription.status === "ENABLED"
                        ? "text-sm font-medium text-accent"
                        : "text-sm secondary-text"
                    }
                  >
                    {subscription.status === "ENABLED" ? "Enabled" : "Disabled"}
                  </span>
                </div>

                <div>
                  <p className="text-sm font-medium">Destination</p>
                  <p className="break-all text-sm text-muted">
                    {subscription.destinationUrl}
                  </p>
                </div>

                <div>
                  <p className="text-sm font-medium">Locations</p>
                  <p className="break-all text-sm text-muted">
                    {subscription.locationIds
                      .map(
                        (locationId) =>
                          locations.find(
                            (location) => location.id === locationId,
                          )?.name ?? "Unavailable location",
                      )
                      .join(", ")}
                  </p>
                </div>

                <p className="text-sm secondary-text">
                  {subscription.eventTypes
                    .map((eventType) => webhookEventLabels[eventType])
                    .join(", ")}
                </p>

                <div className="flex flex-wrap gap-3">
                  <Tooltip delay={500}>
                    <Tooltip.Trigger>
                      <Button
                        isIconOnly
                        aria-label={`Edit webhook ${subscription.name}`}
                        className="rounded-md"
                        variant="tertiary"
                        onPress={() => setEditedSubscriptionId(subscription.id)}
                      >
                        <EditIcon size={17} />
                      </Button>
                    </Tooltip.Trigger>
                    <Tooltip.Content>Edit</Tooltip.Content>
                  </Tooltip>
                  <Tooltip delay={500}>
                    <Tooltip.Trigger>
                      <Button
                        isIconOnly
                        aria-label={`${
                          subscription.status === "ENABLED"
                            ? "Disable"
                            : "Enable"
                        } webhook ${subscription.name}`}
                        className="rounded-md"
                        isPending={
                          pendingAction === `status-${subscription.id}`
                        }
                        variant={
                          subscription.status === "ENABLED"
                            ? "danger"
                            : "secondary"
                        }
                        onPress={() => {
                          if (subscription.status === "ENABLED") {
                            setConfirmation({ kind: "disable", subscription });
                          } else {
                            void changeStatus(subscription, "ENABLED");
                          }
                        }}
                      >
                        {subscription.status === "ENABLED" ? (
                          <DisableIcon size={20} />
                        ) : (
                          <CheckIcon size={20} />
                        )}
                      </Button>
                    </Tooltip.Trigger>
                    <Tooltip.Content>
                      {subscription.status === "ENABLED" ? "Disable" : "Enable"}
                    </Tooltip.Content>
                  </Tooltip>
                  <Button
                    isPending={pendingAction === `rotate-${subscription.id}`}
                    variant="secondary"
                    onPress={() => rotate(subscription)}
                  >
                    Rotate
                  </Button>
                  <Tooltip delay={500}>
                    <Tooltip.Trigger>
                      <Button
                        isIconOnly
                        aria-label={`Delete webhook ${subscription.name}`}
                        className="rounded-md"
                        isPending={
                          pendingAction === `archive-${subscription.id}`
                        }
                        variant="danger"
                        onPress={() =>
                          setConfirmation({ kind: "delete", subscription })
                        }
                      >
                        <TrashIcon size={20} />
                      </Button>
                    </Tooltip.Trigger>
                    <Tooltip.Content>Delete</Tooltip.Content>
                  </Tooltip>
                </div>

                <div className="border-t border-separator pt-3">
                  <p className="mb-2 text-sm font-medium">
                    Signing-secret versions
                  </p>
                  <div className="flex flex-col gap-3">
                    {subscription.signingSecretVersions.map((version) => (
                      <div
                        key={version.id}
                        className="flex flex-wrap items-center justify-between gap-3"
                      >
                        <div>
                          <p className="text-xs text-muted">
                            Issued {formatIntegrationDateTime(version.issuedAt)}
                          </p>
                          <p className="text-xs text-muted">
                            {version.retiredAt
                              ? `Retired ${formatIntegrationDateTime(
                                  version.retiredAt,
                                )}`
                              : version.validUntil
                                ? `Valid until ${formatIntegrationDateTime(
                                    version.validUntil,
                                  )}`
                                : "Current"}
                          </p>
                        </div>
                        {version.validUntil && !version.retiredAt && (
                          <Button
                            isPending={pendingAction === `retire-${version.id}`}
                            variant="danger"
                            onPress={() =>
                              setConfirmation({
                                kind: "retire",
                                subscription,
                                versionId: version.id,
                              })
                            }
                          >
                            Retire
                          </Button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      {editedSubscription && (
        <WebhookEditor
          key={editedSubscription.id}
          isPending={pendingAction === `save-${editedSubscription.id}`}
          locations={enabledLocations}
          subscription={editedSubscription}
          onCancel={() => setEditedSubscriptionId(undefined)}
          onSave={(input) => save(editedSubscription, input)}
        />
      )}

      <AlertDialog
        isOpen={Boolean(confirmation)}
        onOpenChange={(open) => {
          if (!open) setConfirmation(undefined);
        }}
      >
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[440px]">
              <AlertDialog.CloseTrigger />
              <AlertDialog.Header>
                <AlertDialog.Icon
                  status={
                    confirmation?.kind === "disable" ? "warning" : "danger"
                  }
                />
                <AlertDialog.Heading>{confirmationTitle}</AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body>{confirmationDescription}</AlertDialog.Body>
              <AlertDialog.Footer>
                <Button slot="close" variant="tertiary">
                  Cancel
                </Button>
                <Button
                  isPending={isConfirmationPending}
                  variant="danger"
                  onPress={() => void confirmDestructiveAction()}
                >
                  {confirmationLabel}
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
    </div>
  );
}

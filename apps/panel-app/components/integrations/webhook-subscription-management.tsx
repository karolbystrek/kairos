import type { FormEvent } from "react";
import type { Location } from "@/src/api/locations";
import type { PendingOneTimeSecret } from "./one-time-secret";
import type {
  WebhookEventType,
  WebhookSubscription,
  WebhookSubscriptionInput,
} from "@/src/api/webhook-subscriptions";

import {
  Alert,
  Button,
  Chip,
  Input,
  Label,
  Spinner,
  Surface,
  TextField,
} from "@heroui/react";
import { useState } from "react";
import useSWR from "swr";

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
      <TextField
        fullWidth
        isRequired
        isDisabled={isDisabled}
        maxLength={64}
        name="webhook-name"
        value={draft.name}
        onChange={(name) => onChange({ ...draft, name })}
      >
        <Label>Name</Label>
        <Input placeholder="Order updates" />
      </TextField>

      <TextField
        fullWidth
        isRequired
        isDisabled={isDisabled}
        maxLength={2048}
        name="webhook-destination"
        type="url"
        value={draft.destinationUrl}
        onChange={(destinationUrl) => onChange({ ...draft, destinationUrl })}
      >
        <Label>Destination URL</Label>
        <Input
          autoCapitalize="none"
          autoComplete="off"
          placeholder="https://pos.example.com/kairos/events"
          spellCheck={false}
        />
      </TextField>

      <div className="flex flex-col gap-2">
        <p className="text-sm font-medium">Locations</p>
        <div className="flex flex-wrap gap-2">
          {locations.map((location) => (
            <Button
              key={location.id}
              aria-pressed={draft.locationIds.includes(location.id)}
              isDisabled={isDisabled}
              size="sm"
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
              {location.id}
            </Button>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <p className="text-sm font-medium">Events</p>
        <div className="flex flex-wrap gap-2">
          {webhookEventTypes.map((eventType) => (
            <Button
              key={eventType}
              aria-pressed={draft.eventTypes.includes(eventType)}
              isDisabled={isDisabled}
              size="sm"
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
    <Surface className="flex flex-col gap-4">
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
        <div className="flex flex-wrap gap-2">
          <Button isPending={isPending} type="submit">
            {isPending ? "Saving…" : "Save changes"}
          </Button>
          <Button isDisabled={isPending} variant="secondary" onPress={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Surface>
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
  const [draft, setDraft] = useState<SubscriptionDraft>({
    name: "",
    destinationUrl: "",
    locationIds: [],
    eventTypes: [],
  });
  const [editedSubscriptionId, setEditedSubscriptionId] = useState<string>();
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
        eventTypes: [],
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

  async function changeStatus(subscription: WebhookSubscription) {
    if (pendingAction) return;

    const status = subscription.status === "ENABLED" ? "DISABLED" : "ENABLED";

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
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function archive(subscription: WebhookSubscription) {
    if (pendingAction) return;

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
    } catch (caught) {
      setActionError(caught);
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

  async function retire(subscription: WebhookSubscription, versionId: string) {
    if (pendingAction) return;

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
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  return (
    <div className="flex flex-col gap-8">
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

      <section className="flex flex-col gap-4">
        <div>
          <h3 className="text-xl font-semibold">Create webhook subscription</h3>
          <p className="text-sm text-muted">
            New subscriptions start disabled so the recipient can be configured
            before delivery begins.
          </p>
        </div>
        <form className="flex max-w-3xl flex-col gap-4" onSubmit={create}>
          <SubscriptionFields
            draft={draft}
            isDisabled={Boolean(pendingAction)}
            locations={locations}
            onChange={setDraft}
          />
          <Button
            className="self-start"
            isPending={pendingAction === "create"}
            type="submit"
          >
            {pendingAction === "create" ? "Creating…" : "Create subscription"}
          </Button>
        </form>
      </section>

      <section className="flex flex-col gap-4">
        <h3 className="text-xl font-semibold">Webhook subscriptions</h3>

        {areSubscriptionsLoading ? (
          <Spinner aria-label="Loading webhook subscriptions" />
        ) : subscriptions.length === 0 ? (
          <p className="text-muted">No webhook subscriptions configured.</p>
        ) : (
          <div className="grid gap-4 xl:grid-cols-2">
            {subscriptions.map((subscription) => (
              <Surface key={subscription.id} className="flex flex-col gap-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <h4 className="font-semibold">{subscription.name}</h4>
                  <Chip
                    color={
                      subscription.status === "ENABLED" ? "success" : "default"
                    }
                  >
                    {subscription.status === "ENABLED" ? "Enabled" : "Disabled"}
                  </Chip>
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
                    {subscription.locationIds.join(", ")}
                  </p>
                </div>

                <div className="flex flex-wrap gap-2">
                  {subscription.eventTypes.map((eventType) => (
                    <Chip key={eventType}>{webhookEventLabels[eventType]}</Chip>
                  ))}
                </div>

                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    onPress={() => setEditedSubscriptionId(subscription.id)}
                  >
                    Edit
                  </Button>
                  <Button
                    isPending={pendingAction === `status-${subscription.id}`}
                    size="sm"
                    variant="secondary"
                    onPress={() => changeStatus(subscription)}
                  >
                    {subscription.status === "ENABLED" ? "Disable" : "Enable"}
                  </Button>
                  <Button
                    isPending={pendingAction === `rotate-${subscription.id}`}
                    size="sm"
                    variant="secondary"
                    onPress={() => rotate(subscription)}
                  >
                    Rotate signing secret
                  </Button>
                  <Button
                    isPending={pendingAction === `archive-${subscription.id}`}
                    size="sm"
                    variant="danger"
                    onPress={() => archive(subscription)}
                  >
                    Archive
                  </Button>
                </div>

                <div className="border-t border-separator pt-3">
                  <p className="mb-2 text-sm font-medium">
                    Signing-secret versions
                  </p>
                  <div className="flex flex-col gap-2">
                    {subscription.signingSecretVersions.map((version) => (
                      <div
                        key={version.id}
                        className="flex flex-wrap items-center justify-between gap-2"
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
                            size="sm"
                            variant="danger"
                            onPress={() => retire(subscription, version.id)}
                          >
                            Retire now
                          </Button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </Surface>
            ))}
          </div>
        )}
      </section>

      {editedSubscription && (
        <WebhookEditor
          key={editedSubscription.id}
          isPending={pendingAction === `save-${editedSubscription.id}`}
          locations={locations}
          subscription={editedSubscription}
          onCancel={() => setEditedSubscriptionId(undefined)}
          onSave={(input) => save(editedSubscription, input)}
        />
      )}
    </div>
  );
}

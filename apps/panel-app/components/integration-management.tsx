import type { FormEvent } from "react";
import type { Location } from "@/src/api/locations";
import type { ExternalIntegration } from "@/src/api/integrations";
import type { PendingOneTimeSecret } from "./integrations/one-time-secret";

import {
  Alert,
  Button,
  Chip,
  Input,
  Label,
  Spinner,
  Surface,
  Tabs,
  TextField,
} from "@heroui/react";
import { useState } from "react";
import useSWR from "swr";

import { ApiKeyManagement } from "@/components/integrations/api-key-management";
import {
  getIntegrationErrorMessage,
  shouldRetryIntegrationRequest,
} from "@/components/integrations/integration-ui";
import { OneTimeSecret } from "@/components/integrations/one-time-secret";
import { WebhookSubscriptionManagement } from "@/components/integrations/webhook-subscription-management";
import { staffIntegrationsKey, staffLocationsKey } from "@/src/api/cache-keys";
import {
  archiveExternalIntegration,
  createExternalIntegration,
  listExternalIntegrations,
  renameExternalIntegration,
  updateExternalIntegrationStatus,
} from "@/src/api/integrations";
import { listLocations } from "@/src/api/locations";

function IntegrationDetails({
  integration,
  accountId,
  locations,
  onArchived,
  onSecretIssued,
  onUpdated,
}: {
  integration: ExternalIntegration;
  accountId: string;
  locations: Location[];
  onArchived: (integrationId: string) => Promise<void>;
  onSecretIssued: (secret: PendingOneTimeSecret) => void;
  onUpdated: (integration: ExternalIntegration) => Promise<void>;
}) {
  const [name, setName] = useState(integration.name);
  const [pendingAction, setPendingAction] = useState<string>();
  const [actionError, setActionError] = useState<unknown>();

  async function rename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction("rename");

    try {
      const updated = await renameExternalIntegration(integration.id, name);

      await onUpdated(updated);
      setName(updated.name);
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function changeStatus() {
    if (pendingAction) return;

    const status = integration.status === "ENABLED" ? "DISABLED" : "ENABLED";

    setActionError(undefined);
    setPendingAction("status");

    try {
      await onUpdated(
        await updateExternalIntegrationStatus(integration.id, status),
      );
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function archive() {
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction("archive");

    try {
      await archiveExternalIntegration(integration.id);
      await onArchived(integration.id);
    } catch (caught) {
      setActionError(caught);
      setPendingAction(undefined);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      {Boolean(actionError) && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Integration request failed</Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(actionError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <Surface className="flex flex-col gap-5 rounded-2xl p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-2xl font-semibold">{integration.name}</h2>
              <Chip
                color={integration.status === "ENABLED" ? "success" : "default"}
              >
                {integration.status === "ENABLED" ? "Enabled" : "Disabled"}
              </Chip>
            </div>
            <p className="break-all text-sm text-muted">{integration.id}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              isPending={pendingAction === "status"}
              variant="secondary"
              onPress={changeStatus}
            >
              {integration.status === "ENABLED"
                ? "Disable integration"
                : "Enable integration"}
            </Button>
            <Button
              isPending={pendingAction === "archive"}
              variant="danger"
              onPress={archive}
            >
              Archive integration
            </Button>
          </div>
        </div>

        <form
          className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={rename}
        >
          <TextField
            fullWidth
            isRequired
            isDisabled={Boolean(pendingAction)}
            maxLength={64}
            name="integration-name"
            value={name}
            onChange={setName}
          >
            <Label>Name</Label>
            <Input />
          </TextField>
          <Button
            className="shrink-0"
            isPending={pendingAction === "rename"}
            type="submit"
            variant="secondary"
          >
            Save name
          </Button>
        </form>
      </Surface>

      <Tabs>
        <Tabs.ListContainer>
          <Tabs.List aria-label={`${integration.name} credentials`}>
            <Tabs.Tab id="api-keys">
              API Keys
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="webhooks">
              Webhooks
              <Tabs.Indicator />
            </Tabs.Tab>
          </Tabs.List>
        </Tabs.ListContainer>
        <Tabs.Panel className="pt-6" id="api-keys">
          <ApiKeyManagement
            accountId={accountId}
            integrationId={integration.id}
            locations={locations}
            onSecretIssued={onSecretIssued}
          />
        </Tabs.Panel>
        <Tabs.Panel className="pt-6" id="webhooks">
          <WebhookSubscriptionManagement
            accountId={accountId}
            integrationId={integration.id}
            locations={locations}
            onSecretIssued={onSecretIssued}
          />
        </Tabs.Panel>
      </Tabs>
    </div>
  );
}

export function IntegrationManagement({ accountId }: { accountId: string }) {
  const [name, setName] = useState("");
  const [selectedIntegrationId, setSelectedIntegrationId] = useState<string>();
  const [pendingSecret, setPendingSecret] = useState<PendingOneTimeSecret>();
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState<unknown>();

  const {
    data: integrations = [],
    error: integrationsError,
    isLoading: areIntegrationsLoading,
    isValidating: areIntegrationsValidating,
    mutate: mutateIntegrations,
  } = useSWR(staffIntegrationsKey(accountId), listExternalIntegrations, {
    errorRetryCount: 3,
    shouldRetryOnError: shouldRetryIntegrationRequest,
  });
  const {
    data: locations = [],
    error: locationsError,
    isLoading: areLocationsLoading,
  } = useSWR(staffLocationsKey(accountId), listLocations, {
    errorRetryCount: 3,
    shouldRetryOnError: shouldRetryIntegrationRequest,
  });

  const selectedIntegration =
    integrations.find(
      (integration) => integration.id === selectedIntegrationId,
    ) ?? integrations[0];
  const error = integrationsError ?? locationsError ?? createError;

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isCreating) return;

    setCreateError(undefined);
    setIsCreating(true);

    try {
      const created = await createExternalIntegration(name);

      await mutateIntegrations(
        (current) => [
          created,
          ...(current ?? []).filter(
            (integration) => integration.id !== created.id,
          ),
        ],
        { revalidate: false },
      );
      setSelectedIntegrationId(created.id);
      setName("");
    } catch (caught) {
      setCreateError(caught);
    } finally {
      setIsCreating(false);
    }
  }

  async function updateIntegration(updated: ExternalIntegration) {
    await mutateIntegrations(
      (current) =>
        (current ?? [updated]).map((integration) =>
          integration.id === updated.id ? updated : integration,
        ),
      { revalidate: false },
    );
  }

  async function removeIntegration(integrationId: string) {
    await mutateIntegrations(
      (current) =>
        (current ?? []).filter(
          (integration) => integration.id !== integrationId,
        ),
      { revalidate: false },
    );
    setSelectedIntegrationId(undefined);
  }

  if (pendingSecret) {
    return (
      <OneTimeSecret
        secret={pendingSecret}
        onConfirmed={() => {
          pendingSecret.afterConfirmed?.();
          setPendingSecret(undefined);
        }}
      />
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h2 className="text-2xl font-semibold">External Integrations</h2>
        <p className="text-muted">
          Configure API access and outbound webhook delivery for third-party
          systems.
        </p>
      </div>

      {Boolean(error) && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Integration management unavailable</Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(error)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <section className="flex flex-col gap-4">
        <h3 className="text-xl font-semibold">Create integration</h3>
        <form
          className="flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={create}
        >
          <TextField
            fullWidth
            isRequired
            isDisabled={isCreating}
            maxLength={64}
            name="new-integration-name"
            value={name}
            onChange={setName}
          >
            <Label>Name</Label>
            <Input placeholder="Restaurant POS" />
          </TextField>
          <Button className="shrink-0" isPending={isCreating} type="submit">
            {isCreating ? "Creating…" : "Create integration"}
          </Button>
        </form>
      </section>

      {areIntegrationsLoading || areLocationsLoading ? (
        <Spinner aria-label="Loading integrations" />
      ) : integrations.length === 0 ? (
        <Alert status="default">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>No integrations yet</Alert.Title>
            <Alert.Description>
              Create an integration before issuing credentials or configuring
              webhooks.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      ) : (
        <>
          <section className="flex flex-col gap-3">
            <div>
              <h3 className="text-xl font-semibold">Integrations</h3>
              {areIntegrationsValidating && (
                <p className="text-sm text-muted">Refreshing integrations…</p>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              {integrations.map((integration) => (
                <Button
                  key={integration.id}
                  variant={
                    integration.id === selectedIntegration?.id
                      ? "primary"
                      : "secondary"
                  }
                  onPress={() => setSelectedIntegrationId(integration.id)}
                >
                  {integration.name}
                </Button>
              ))}
            </div>
          </section>

          {locations.length === 0 && (
            <Alert status="warning">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>No locations available</Alert.Title>
                <Alert.Description>
                  API Keys and webhook subscriptions require at least one tenant
                  location.
                </Alert.Description>
              </Alert.Content>
            </Alert>
          )}

          {selectedIntegration && (
            <IntegrationDetails
              key={selectedIntegration.id}
              accountId={accountId}
              integration={selectedIntegration}
              locations={locations}
              onArchived={removeIntegration}
              onSecretIssued={setPendingSecret}
              onUpdated={updateIntegration}
            />
          )}
        </>
      )}
    </div>
  );
}

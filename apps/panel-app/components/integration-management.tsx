import type { FormEvent } from "react";
import type { Location } from "@/src/api/locations";
import type { ExternalIntegration } from "@/src/api/integrations";
import type { PendingOneTimeSecret } from "./integrations/one-time-secret";

import { Alert, Button, Spinner, Tabs, Tooltip } from "@heroui/react";
import {
  ArrowRight as ArrowRightIcon,
  Ban as DisableIcon,
  Check as CheckIcon,
  Pencil as EditIcon,
  Plus as PlusIcon,
  Trash2 as TrashIcon,
  X as CloseIcon,
} from "lucide-react";
import { useState } from "react";
import useSWR from "swr";

import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
import { ApiKeyManagement } from "@/components/integrations/api-key-management";
import {
  getIntegrationErrorMessage,
  shouldRetryIntegrationRequest,
} from "@/components/integrations/integration-ui";
import { OneTimeSecret } from "@/components/integrations/one-time-secret";
import { WebhookSubscriptionManagement } from "@/components/integrations/webhook-subscription-management";
import { PanelCard } from "@/components/panel-card";
import { PanelDetailHeader } from "@/components/panel-detail-header";
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
  onDeleted,
  onSecretIssued,
  onUpdated,
}: {
  integration: ExternalIntegration;
  accountId: string;
  locations: Location[];
  onDeleted: (integrationId: string) => Promise<void>;
  onSecretIssued: (secret: PendingOneTimeSecret) => void;
  onUpdated: (integration: ExternalIntegration) => Promise<void>;
}) {
  const [name, setName] = useState(integration.name);
  const [isEditingName, setIsEditingName] = useState(false);
  const [confirmation, setConfirmation] = useState<"disable" | "delete">();
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
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
      setIsEditingName(false);
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function changeStatus(): Promise<boolean> {
    if (pendingAction) return false;

    const status = integration.status === "ENABLED" ? "DISABLED" : "ENABLED";

    setActionError(undefined);
    setPendingAction("status");

    try {
      await onUpdated(
        await updateExternalIntegrationStatus(integration.id, status),
      );

      return true;
    } catch (caught) {
      setActionError(caught);

      return false;
    } finally {
      setPendingAction(undefined);
    }
  }

  async function remove(): Promise<boolean> {
    if (pendingAction) return false;

    setActionError(undefined);
    setPendingAction("delete");

    try {
      await archiveExternalIntegration(integration.id);
      await onDeleted(integration.id);

      return true;
    } catch (caught) {
      setActionError(caught);

      return false;
    } finally {
      setPendingAction(undefined);
    }
  }

  function closeConfirmation() {
    setConfirmation(undefined);
    setDeleteConfirmation("");
  }

  function cancelRename() {
    setName(integration.name);
    setIsEditingName(false);
  }

  return (
    <div className="flex flex-col gap-6">
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

      <PanelDetailHeader
        eyebrow="Integration"
        title={integration.name}
        titleEditor={
          isEditingName ? (
            <form
              className="control-row control-row--touch min-w-0 w-full sm:max-w-xl"
              onSubmit={rename}
            >
              <FormTextField
                fullWidth
                isRequired
                isDisabled={pendingAction === "rename"}
                label="Integration name"
                maxLength={64}
                name="integration-name"
                value={name}
                onChange={setName}
              />
              <Tooltip delay={500}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    aria-label="Confirm name"
                    className="shrink-0 rounded-md"
                    isPending={pendingAction === "rename"}
                    type="submit"
                  >
                    <CheckIcon size={19} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Confirm</Tooltip.Content>
              </Tooltip>
              <Tooltip delay={500}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    aria-label="Cancel editing name"
                    className="shrink-0 rounded-md"
                    isDisabled={pendingAction === "rename"}
                    variant="tertiary"
                    onPress={cancelRename}
                  >
                    <CloseIcon size={19} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Cancel</Tooltip.Content>
              </Tooltip>
            </form>
          ) : undefined
        }
        trailingActions={
          <>
            {!isEditingName && (
              <Tooltip delay={500}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    aria-label="Edit name"
                    className="shrink-0 rounded-md"
                    variant="tertiary"
                    onPress={() => setIsEditingName(true)}
                  >
                    <EditIcon size={17} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Edit name</Tooltip.Content>
              </Tooltip>
            )}
            <Tooltip delay={500}>
              <Tooltip.Trigger>
                <Button
                  isIconOnly
                  aria-label={`${
                    integration.status === "ENABLED" ? "Disable" : "Enable"
                  } integration ${integration.name}`}
                  className="rounded-md"
                  isPending={pendingAction === "status"}
                  variant={
                    integration.status === "ENABLED" ? "danger" : "secondary"
                  }
                  onPress={() => {
                    if (pendingAction) return;
                    if (integration.status === "ENABLED") {
                      setConfirmation("disable");
                    } else {
                      void changeStatus();
                    }
                  }}
                >
                  {integration.status === "ENABLED" ? (
                    <DisableIcon size={20} />
                  ) : (
                    <CheckIcon size={20} />
                  )}
                </Button>
              </Tooltip.Trigger>
              <Tooltip.Content>
                {integration.status === "ENABLED" ? "Disable" : "Enable"}
              </Tooltip.Content>
            </Tooltip>
            <Tooltip delay={500}>
              <Tooltip.Trigger>
                <Button
                  isIconOnly
                  aria-label={`Delete integration ${integration.name}`}
                  className="rounded-md"
                  isPending={pendingAction === "delete"}
                  variant="danger"
                  onPress={() => {
                    if (pendingAction) return;
                    setConfirmation("delete");
                  }}
                >
                  <TrashIcon size={20} />
                </Button>
              </Tooltip.Trigger>
              <Tooltip.Content>Delete</Tooltip.Content>
            </Tooltip>
          </>
        }
      />

      <Tabs>
        <Tabs.ListContainer>
          <Tabs.List aria-label={`${integration.name} credentials`}>
            <Tabs.Tab id="api-keys">
              <span className="tab-motion-content">API Keys</span>
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="webhooks">
              <span className="tab-motion-content">Webhooks</span>
              <Tabs.Indicator />
            </Tabs.Tab>
          </Tabs.List>
        </Tabs.ListContainer>
        <Tabs.Panel className="pt-5" id="api-keys">
          <ApiKeyManagement
            accountId={accountId}
            integrationId={integration.id}
            locations={locations}
            onSecretIssued={onSecretIssued}
          />
        </Tabs.Panel>
        <Tabs.Panel className="pt-5" id="webhooks">
          <WebhookSubscriptionManagement
            accountId={accountId}
            integrationId={integration.id}
            locations={locations}
            onSecretIssued={onSecretIssued}
          />
        </Tabs.Panel>
      </Tabs>

      <PanelPopup
        className="sm:max-w-[420px]"
        isOpen={confirmation === "disable"}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) closeConfirmation();
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="warning" />
          <PanelPopup.Heading>Disable {integration.name}?</PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body>
          Its API Keys will stop authenticating and future webhook fan-out will
          pause until the integration is enabled again.
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button slot="close" variant="tertiary">
            Cancel
          </Button>
          <Button
            isPending={pendingAction === "status"}
            variant="danger"
            onPress={() => {
              void changeStatus().then((changed) => {
                if (changed) closeConfirmation();
              });
            }}
          >
            Disable
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>

      <PanelPopup
        className="sm:max-w-[460px]"
        isOpen={confirmation === "delete"}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) closeConfirmation();
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="danger" />
          <PanelPopup.Heading>Delete {integration.name}?</PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            This permanently removes the integration from this workspace and
            immediately blocks its API Keys and future webhook fan-out. Type its
            exact name to confirm.
          </p>
          <FormTextField
            fullWidth
            isRequired
            inputProps={{ autoComplete: "off" }}
            isDisabled={pendingAction === "delete"}
            label={`Type ${integration.name} to confirm`}
            name="delete-integration-confirmation"
            value={deleteConfirmation}
            onChange={setDeleteConfirmation}
          />
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button slot="close" variant="tertiary">
            Cancel
          </Button>
          <Button
            isDisabled={deleteConfirmation !== integration.name}
            isPending={pendingAction === "delete"}
            variant="danger"
            onPress={() => {
              void remove().then((removed) => {
                if (removed) closeConfirmation();
              });
            }}
          >
            Delete
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>
    </div>
  );
}

export function IntegrationManagement({ accountId }: { accountId: string }) {
  const [name, setName] = useState("");
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedIntegrationId, setSelectedIntegrationId] = useState<string>();
  const [pendingSecret, setPendingSecret] = useState<PendingOneTimeSecret>();
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState<unknown>();

  const {
    data: integrations = [],
    error: integrationsError,
    isLoading: areIntegrationsLoading,
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
  const enabledLocations = locations.filter(
    (location) => location.status === "ENABLED",
  );
  const error = integrationsError ?? locationsError ?? createError;

  function openCreate() {
    setCreateError(undefined);
    setIsCreateOpen(true);
  }

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
      setIsCreateOpen(false);
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
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="page-title">Integrations</h1>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label="New integration"
              className="rounded-md"
              size="lg"
              onPress={openCreate}
            >
              <PlusIcon size={20} />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content>New integration</Tooltip.Content>
        </Tooltip>
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

      {areIntegrationsLoading || areLocationsLoading ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Loading integrations" />
        </div>
      ) : integrations.length === 0 ? (
        <div className="py-12">
          <h2 className="section-title">No integrations yet</h2>
          <p className="mt-2 max-w-sm secondary-text">
            Connect a point-of-sale system when you&apos;re ready.
          </p>
        </div>
      ) : (
        <div className="grid gap-6 md:grid-cols-[minmax(220px,0.65fr)_minmax(0,1.35fr)]">
          <section className="border-t border-separator pt-2 md:border-r md:border-t-0 md:pr-6 md:pt-0">
            {integrations.map((integration) => (
              <PanelCard
                key={integration.id}
                accessibilityLabel={`View integration ${integration.name}`}
                isSelected={integration.id === selectedIntegration?.id}
                metadata={
                  <span
                    className={
                      integration.status === "ENABLED"
                        ? "text-accent"
                        : "secondary-text"
                    }
                  >
                    {integration.status === "ENABLED" ? "Enabled" : "Disabled"}
                  </span>
                }
                title={integration.name}
                trailing={<ArrowRightIcon size={17} />}
                onPress={() => setSelectedIntegrationId(integration.id)}
              />
            ))}
          </section>

          <div className="min-w-0">
            {enabledLocations.length === 0 && (
              <p className="mb-5 text-sm text-muted">
                Enable a location in Locations before adding API Keys or
                webhooks.
              </p>
            )}
            {selectedIntegration && (
              <IntegrationDetails
                key={selectedIntegration.id}
                accountId={accountId}
                integration={selectedIntegration}
                locations={locations}
                onDeleted={removeIntegration}
                onSecretIssued={setPendingSecret}
                onUpdated={updateIntegration}
              />
            )}
          </div>
        </div>
      )}

      <PanelPopup
        isOpen={isCreateOpen}
        size="sm"
        onOpenChange={setIsCreateOpen}
      >
        <PanelPopup.Header>
          <PanelPopup.Heading>New integration</PanelPopup.Heading>
        </PanelPopup.Header>
        <form onSubmit={create}>
          <PanelPopup.Body className="flex flex-col gap-4">
            {Boolean(createError) && (
              <Alert status="danger">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title>Integration could not be created</Alert.Title>
                  <Alert.Description>
                    {getIntegrationErrorMessage(createError)}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}
            <FormTextField
              fullWidth
              isRequired
              isDisabled={isCreating}
              label="Integration name"
              maxLength={64}
              name="new-integration-name"
              value={name}
              onChange={setName}
            />
          </PanelPopup.Body>
          <PanelPopup.Footer>
            <Button slot="close" variant="tertiary">
              Cancel
            </Button>
            <Button isPending={isCreating} type="submit">
              <PlusIcon size={18} />
              {isCreating ? "Creating…" : "Create"}
            </Button>
          </PanelPopup.Footer>
        </form>
      </PanelPopup>
    </div>
  );
}

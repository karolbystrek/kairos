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
            <Alert.Title>
              Nie udało się wykonać operacji na integracji
            </Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(actionError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <PanelDetailHeader
        eyebrow="Integracja"
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
                label="Nazwa integracji"
                maxLength={64}
                name="integration-name"
                value={name}
                onChange={setName}
              />
              <Tooltip delay={500}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    aria-label="Zatwierdź nazwę"
                    className="shrink-0 rounded-md"
                    isPending={pendingAction === "rename"}
                    type="submit"
                  >
                    <CheckIcon size={19} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Potwierdź</Tooltip.Content>
              </Tooltip>
              <Tooltip delay={500}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    aria-label="Anuluj edycję nazwy"
                    className="shrink-0 rounded-md"
                    isDisabled={pendingAction === "rename"}
                    variant="tertiary"
                    onPress={cancelRename}
                  >
                    <CloseIcon size={19} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Anuluj</Tooltip.Content>
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
                    aria-label="Edytuj nazwę"
                    className="shrink-0 rounded-md"
                    variant="tertiary"
                    onPress={() => setIsEditingName(true)}
                  >
                    <EditIcon size={17} />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>Edytuj nazwę</Tooltip.Content>
              </Tooltip>
            )}
            <Tooltip delay={500}>
              <Tooltip.Trigger>
                <Button
                  isIconOnly
                  aria-label={`${
                    integration.status === "ENABLED" ? "Wyłącz" : "Włącz"
                  } integrację ${integration.name}`}
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
                {integration.status === "ENABLED" ? "Wyłącz" : "Włącz"}
              </Tooltip.Content>
            </Tooltip>
            <Tooltip delay={500}>
              <Tooltip.Trigger>
                <Button
                  isIconOnly
                  aria-label={`Usuń integrację ${integration.name}`}
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
              <Tooltip.Content>Usuń</Tooltip.Content>
            </Tooltip>
          </>
        }
      />

      <p className="text-sm text-muted">
        Status: {integration.status === "ENABLED" ? "Włączona" : "Wyłączona"}
      </p>

      <Tabs>
        <Tabs.ListContainer>
          <Tabs.List aria-label={`${integration.name} — klucze i webhooki`}>
            <Tabs.Tab id="api-keys">
              <span className="tab-motion-content">Klucze API</span>
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="webhooks">
              <span className="tab-motion-content">Webhooki</span>
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
          <PanelPopup.Heading>
            Wyłączyć integrację {integration.name}?
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            Klucze API przestaną działać, a wysyłanie nowych webhooków zostanie
            wstrzymane do ponownego włączenia integracji.
          </p>
          {Boolean(actionError) && (
            <Alert status="danger">
              <Alert.Content>
                <Alert.Title>Nie udało się wyłączyć integracji</Alert.Title>
                <Alert.Description>
                  {getIntegrationErrorMessage(actionError)}
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
            isPending={pendingAction === "status"}
            variant="danger"
            onPress={() => {
              void changeStatus().then((changed) => {
                if (changed) closeConfirmation();
              });
            }}
          >
            Wyłącz
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
          <PanelPopup.Heading>
            Usunąć integrację {integration.name}?
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p>
            Integracja zostanie trwale usunięta z panelu. Jej klucze API i
            wysyłanie nowych webhooków zostaną natychmiast zablokowane. Aby
            potwierdzić, wpisz dokładną nazwę integracji.
          </p>
          <FormTextField
            fullWidth
            isRequired
            inputProps={{ autoComplete: "off" }}
            isDisabled={pendingAction === "delete"}
            label={`Wpisz ${integration.name} w celu potwierdzenia`}
            name="delete-integration-confirmation"
            value={deleteConfirmation}
            onChange={setDeleteConfirmation}
          />
          {Boolean(actionError) && (
            <Alert status="danger">
              <Alert.Content>
                <Alert.Title>Nie udało się usunąć integracji</Alert.Title>
                <Alert.Description>
                  {getIntegrationErrorMessage(actionError)}
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
            isDisabled={deleteConfirmation !== integration.name}
            isPending={pendingAction === "delete"}
            variant="danger"
            onPress={() => {
              void remove().then((removed) => {
                if (removed) closeConfirmation();
              });
            }}
          >
            Usuń
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

  const selectedIntegration = integrations.find(
    (integration) => integration.id === selectedIntegrationId,
  );
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
        <h1 className="page-title">Integracje</h1>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label="Nowa integracja"
              className="rounded-md"
              size="lg"
              onPress={openCreate}
            >
              <PlusIcon size={20} />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content>Nowa integracja</Tooltip.Content>
        </Tooltip>
      </div>

      {Boolean(error) && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Zarządzanie integracjami jest niedostępne</Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(error)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {areIntegrationsLoading || areLocationsLoading ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Wczytywanie integracji" />
        </div>
      ) : integrations.length === 0 ? (
        <div className="py-12">
          <h2 className="section-title">Brak integracji</h2>
          <p className="mt-2 max-w-sm secondary-text">
            Podłącz system sprzedażowy, gdy będziesz gotowy.
          </p>
        </div>
      ) : (
        <>
          <section aria-label="Integracje" className="entity-card-grid">
            {integrations.map((integration) => (
              <PanelCard
                key={integration.id}
                accessibilityLabel={`Pokaż integrację ${integration.name}, ${integration.status === "ENABLED" ? "włączona" : "wyłączona"}`}
                isSelected={integration.id === selectedIntegration?.id}
                status={
                  integration.status === "ENABLED" ? "ENABLED" : "DISABLED"
                }
                title={integration.name}
                trailing={<ArrowRightIcon size={17} />}
                onPress={() => setSelectedIntegrationId(integration.id)}
              />
            ))}
          </section>

          {selectedIntegration && (
            <PanelPopup
              isOpen
              aria-label={`Integracja ${selectedIntegration.name}`}
              size="lg"
              onOpenChange={(open) => {
                if (!open) setSelectedIntegrationId(undefined);
              }}
            >
              <PanelPopup.Body className="pb-6 pt-12">
                {enabledLocations.length === 0 && (
                  <p className="mb-5 text-sm text-muted">
                    Włącz lokal w zakładce Lokale, aby dodać klucze API lub
                    webhooki.
                  </p>
                )}
                <IntegrationDetails
                  key={selectedIntegration.id}
                  accountId={accountId}
                  integration={selectedIntegration}
                  locations={locations}
                  onDeleted={removeIntegration}
                  onSecretIssued={setPendingSecret}
                  onUpdated={updateIntegration}
                />
              </PanelPopup.Body>
            </PanelPopup>
          )}
        </>
      )}

      <PanelPopup
        isOpen={isCreateOpen}
        size="sm"
        onOpenChange={setIsCreateOpen}
      >
        <PanelPopup.Header>
          <PanelPopup.Heading>Nowa integracja</PanelPopup.Heading>
        </PanelPopup.Header>
        <form onSubmit={create}>
          <PanelPopup.Body className="flex flex-col gap-4">
            {Boolean(createError) && (
              <Alert status="danger">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title>Nie udało się utworzyć integracji</Alert.Title>
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
              label="Nazwa integracji"
              maxLength={64}
              name="new-integration-name"
              value={name}
              onChange={setName}
            />
          </PanelPopup.Body>
          <PanelPopup.Footer>
            <Button slot="close" variant="tertiary">
              Anuluj
            </Button>
            <Button isPending={isCreating} type="submit">
              <PlusIcon size={18} />
              {isCreating ? "Tworzenie…" : "Utwórz"}
            </Button>
          </PanelPopup.Footer>
        </form>
      </PanelPopup>
    </div>
  );
}

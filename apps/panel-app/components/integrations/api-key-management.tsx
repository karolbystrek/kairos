import type { FormEvent } from "react";
import type { Location } from "@/src/api/locations";
import type { PendingOneTimeSecret } from "./one-time-secret";

import {
  Alert,
  Button,
  Input,
  Label,
  Radio,
  RadioGroup,
  Spinner,
  TextField,
} from "@heroui/react";
import { Plus, Trash2 as TrashIcon } from "lucide-react";
import { useState } from "react";
import useSWR from "swr";

import { HoldToConfirmButton } from "@/components/hold-to-confirm-button";
import { FormTextField } from "@/components/form-controls";
import {
  formatIntegrationDateTime,
  getIntegrationErrorMessage,
  shouldRetryIntegrationRequest,
} from "@/components/integrations/integration-ui";
import {
  deleteApiKey,
  issueApiKey,
  listApiKeys,
  type ApiKey,
} from "@/src/api/api-keys";
import { ApiError } from "@/src/api/api-fetch";
import { staffApiKeysKey } from "@/src/api/cache-keys";

function apiKeyAccessLabel(apiKey: ApiKey): string {
  return apiKey.scopes.includes("orders:write")
    ? "Odczyt i zapis zamówień"
    : "Odczyt zamówień";
}

function isExpired(apiKey: ApiKey): boolean {
  return (
    apiKey.expiresAt !== null &&
    new Date(apiKey.expiresAt).getTime() <= Date.now()
  );
}

export function ApiKeyManagement({
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
  const [name, setName] = useState("");
  const [accessMode, setAccessMode] = useState<"READ" | "WRITE">("READ");
  const [selectedLocationIds, setSelectedLocationIds] = useState<string[]>([]);
  const [expiration, setExpiration] = useState("");
  const [apiKeyToDelete, setApiKeyToDelete] = useState<ApiKey>();
  const [pendingAction, setPendingAction] = useState<string>();
  const [actionError, setActionError] = useState<unknown>();

  const apiKeysCacheKey = staffApiKeysKey(accountId, integrationId);
  const {
    data: apiKeys = [],
    error: apiKeysError,
    isLoading: areApiKeysLoading,
    mutate: mutateApiKeys,
  } = useSWR(apiKeysCacheKey, () => listApiKeys(integrationId), {
    errorRetryCount: 3,
    shouldRetryOnError: shouldRetryIntegrationRequest,
  });

  const error = apiKeysError ?? actionError;

  function toggleLocation(locationId: string) {
    setSelectedLocationIds((current) =>
      current.includes(locationId)
        ? current.filter((id) => id !== locationId)
        : [...current, locationId],
    );
  }

  async function issue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction("issue");

    try {
      const expiresAt = expiration ? new Date(expiration).toISOString() : null;
      const result = await issueApiKey(integrationId, {
        name,
        scopes: accessMode === "WRITE" ? ["orders:write"] : ["orders:read"],
        locationIds: selectedLocationIds,
        expiresAt,
      });

      onSecretIssued({
        title: `Utworzono klucz API: ${result.apiKey.name}`,
        description:
          "Zapisz ten klucz w systemie zewnętrznym, który będzie korzystać z API Kairos.",
        value: result.secret,
      });
      await mutateApiKeys(
        (current) => [
          result.apiKey,
          ...(current ?? []).filter((key) => key.id !== result.apiKey.id),
        ],
        { revalidate: false },
      );
      setName("");
      setAccessMode("READ");
      setSelectedLocationIds([]);
      setExpiration("");
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function remove(apiKey: ApiKey): Promise<boolean> {
    if (pendingAction) return false;

    setActionError(undefined);
    setPendingAction(`delete-${apiKey.id}`);

    try {
      await deleteApiKey(apiKey.id);
      await mutateApiKeys(
        (current) => (current ?? []).filter((key) => key.id !== apiKey.id),
        { revalidate: false },
      );

      return true;
    } catch (caught) {
      // A key deleted elsewhere is already gone; drop it instead of showing an error.
      if (caught instanceof ApiError && caught.status === 404) {
        await mutateApiKeys(
          (current) => (current ?? []).filter((key) => key.id !== apiKey.id),
          { revalidate: false },
        );

        return true;
      }
      setActionError(caught);

      return false;
    } finally {
      setPendingAction(undefined);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {Boolean(error) && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>
              Nie udało się wykonać operacji na kluczu API
            </Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(error)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <section className="flex flex-col gap-3">
        <div>
          <h3 className="text-xl font-semibold">Utwórz klucz API</h3>
          <p className="text-sm text-muted">
            Uprawnień nie można później zmienić.
          </p>
        </div>

        <form className="flex max-w-3xl flex-col gap-4" onSubmit={issue}>
          <FormTextField
            fullWidth
            isRequired
            isDisabled={Boolean(pendingAction)}
            label="Nazwa klucza API"
            maxLength={64}
            name="api-key-name"
            value={name}
            onChange={setName}
          />

          <RadioGroup
            isDisabled={Boolean(pendingAction)}
            name="api-key-access"
            orientation="horizontal"
            value={accessMode}
            onChange={(value) =>
              setAccessMode(value === "WRITE" ? "WRITE" : "READ")
            }
          >
            <Label>Dostęp do zamówień</Label>
            <Radio value="READ">
              <Radio.Content>
                <Radio.Control>
                  <Radio.Indicator />
                </Radio.Control>
                Odczyt
              </Radio.Content>
            </Radio>
            <Radio value="WRITE">
              <Radio.Content>
                <Radio.Control>
                  <Radio.Indicator />
                </Radio.Control>
                Odczyt i zapis
              </Radio.Content>
            </Radio>
          </RadioGroup>

          <div className="flex flex-col gap-3">
            <p className="text-sm font-medium">Lokale</p>
            <div className="flex flex-wrap gap-3">
              {enabledLocations.map((location) => (
                <Button
                  key={location.id}
                  aria-pressed={selectedLocationIds.includes(location.id)}
                  isDisabled={Boolean(pendingAction)}
                  variant={
                    selectedLocationIds.includes(location.id)
                      ? "primary"
                      : "secondary"
                  }
                  onPress={() => toggleLocation(location.id)}
                >
                  {location.name}
                </Button>
              ))}
            </div>
          </div>

          <TextField
            fullWidth
            className="max-w-sm"
            isDisabled={Boolean(pendingAction)}
            name="api-key-expiration"
            type="datetime-local"
            value={expiration}
            onChange={setExpiration}
          >
            <Label>Data wygaśnięcia (opcjonalnie)</Label>
            <Input />
          </TextField>

          <Button
            className="self-start"
            isDisabled={enabledLocations.length === 0}
            isPending={pendingAction === "issue"}
            type="submit"
          >
            <Plus size={18} />
            {pendingAction === "issue" ? "Tworzenie…" : "Utwórz"}
          </Button>
        </form>
      </section>

      <section className="flex flex-col gap-3">
        <h3 className="text-xl font-semibold">Klucze API</h3>

        {areApiKeysLoading ? (
          <Spinner aria-label="Wczytywanie kluczy API" />
        ) : apiKeys.length === 0 ? (
          <p className="text-muted">Brak kluczy API.</p>
        ) : (
          <div className="border-t border-separator">
            {apiKeys.map((apiKey) => (
              <article
                key={apiKey.id}
                className="flex flex-col gap-3 border-b border-separator py-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <h4 className="font-semibold">{apiKey.name}</h4>
                  <span
                    className={
                      isExpired(apiKey)
                        ? "text-sm text-danger"
                        : "text-sm font-medium text-accent"
                    }
                  >
                    {isExpired(apiKey) ? "Wygasł" : "Aktywny"}
                  </span>
                </div>

                <div>
                  <p className="text-sm font-medium">Dostęp do zamówień</p>
                  <p className="text-sm text-muted">
                    {apiKeyAccessLabel(apiKey)}
                  </p>
                </div>

                <div>
                  <p className="text-sm font-medium">Lokale</p>
                  <p className="break-all text-sm text-muted">
                    {apiKey.locationIds
                      .map(
                        (locationId) =>
                          locations.find(
                            (location) => location.id === locationId,
                          )?.name ?? "Niedostępny lokal",
                      )
                      .join(", ")}
                  </p>
                </div>

                <div className="text-sm text-muted">
                  <p>Utworzono {formatIntegrationDateTime(apiKey.createdAt)}</p>
                  <p>
                    {apiKey.expiresAt
                      ? `Wygasa ${formatIntegrationDateTime(apiKey.expiresAt)}`
                      : "Nie wygasa"}
                  </p>
                  <p>
                    {apiKey.lastUsedAt
                      ? `Ostatnio użyty ${formatIntegrationDateTime(apiKey.lastUsedAt)}`
                      : "Nie był jeszcze użyty"}
                  </p>
                </div>

                <div className="flex flex-wrap gap-3">
                  <Button
                    isDisabled={Boolean(pendingAction)}
                    isPending={pendingAction === `delete-${apiKey.id}`}
                    variant="danger"
                    onPress={() => {
                      if (!pendingAction) setApiKeyToDelete(apiKey);
                    }}
                  >
                    <TrashIcon size={18} />
                    Usuń
                  </Button>
                </div>
                {apiKeyToDelete && apiKeyToDelete.id === apiKey.id && (
                  <section
                    aria-label={`Usunięcie klucza ${apiKeyToDelete.name}`}
                    className="flex flex-col gap-3"
                  >
                    <p className="text-sm text-muted">
                      Klucz {apiKeyToDelete.name} natychmiast przestanie
                      działać. Tej operacji nie można cofnąć.
                    </p>
                    <div className="confirmation-actions">
                      <HoldToConfirmButton
                        key={apiKeyToDelete.id}
                        isDisabled={Boolean(pendingAction)}
                        isPending={
                          pendingAction === `delete-${apiKeyToDelete.id}`
                        }
                        onConfirm={async () => {
                          if (await remove(apiKeyToDelete))
                            setApiKeyToDelete(undefined);
                        }}
                      >
                        <TrashIcon size={18} />
                        Przytrzymaj, aby usunąć klucz
                      </HoldToConfirmButton>
                      <Button
                        aria-label="Anuluj usunięcie klucza"
                        isDisabled={Boolean(pendingAction)}
                        variant="tertiary"
                        onPress={() => setApiKeyToDelete(undefined)}
                      >
                        Anuluj
                      </Button>
                    </div>
                  </section>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

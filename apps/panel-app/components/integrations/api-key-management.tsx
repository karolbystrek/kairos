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
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";

import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
import {
  formatIntegrationDateTime,
  getIntegrationErrorMessage,
  shouldRetryIntegrationRequest,
} from "@/components/integrations/integration-ui";
import {
  issueApiKey,
  listApiKeys,
  listApiKeyVersions,
  revokeApiKey,
  rotateApiKey,
  type ApiKey,
} from "@/src/api/api-keys";
import { staffApiKeysKey, staffApiKeyVersionsKey } from "@/src/api/cache-keys";

function replaceApiKey(keys: ApiKey[] | undefined, updated: ApiKey): ApiKey[] {
  return (keys ?? [updated]).map((key) =>
    key.id === updated.id ? updated : key,
  );
}

function apiKeyAccessLabel(apiKey: ApiKey): string {
  return apiKey.scopes.includes("orders:write")
    ? "Read and write orders"
    : "Read orders";
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
  const { mutate: mutateCache } = useSWRConfig();
  const enabledLocations = locations.filter(
    (location) => location.status === "ENABLED",
  );
  const [name, setName] = useState("");
  const [accessMode, setAccessMode] = useState<"READ" | "WRITE">("READ");
  const [selectedLocationIds, setSelectedLocationIds] = useState<string[]>([]);
  const [expiration, setExpiration] = useState("");
  const [selectedApiKeyId, setSelectedApiKeyId] = useState<string>();
  const [apiKeyToRevoke, setApiKeyToRevoke] = useState<ApiKey>();
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

  const selectedApiKey = apiKeys.find((key) => key.id === selectedApiKeyId);
  const versionsCacheKey = selectedApiKey
    ? staffApiKeyVersionsKey(accountId, selectedApiKey.id)
    : null;
  const {
    data: versions = [],
    error: versionsError,
    isLoading: areVersionsLoading,
  } = useSWR(
    versionsCacheKey,
    () => listApiKeyVersions(selectedApiKey?.id ?? ""),
    {
      errorRetryCount: 3,
      shouldRetryOnError: shouldRetryIntegrationRequest,
    },
  );

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
        title: `API Key issued: ${result.apiKey.name}`,
        description:
          "Store this credential in the external system that will call the Kairos API.",
        value: result.secret,
      });
      await mutateApiKeys(
        (current) => [
          result.apiKey,
          ...(current ?? []).filter((key) => key.id !== result.apiKey.id),
        ],
        { revalidate: false },
      );
      await mutateCache(
        staffApiKeyVersionsKey(accountId, result.apiKey.id),
        [result.version],
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

  async function revoke(apiKey: ApiKey): Promise<boolean> {
    if (pendingAction) return false;

    setActionError(undefined);
    setPendingAction(`revoke-${apiKey.id}`);

    try {
      const revoked = await revokeApiKey(apiKey.id);

      await mutateApiKeys((current) => replaceApiKey(current, revoked), {
        revalidate: false,
      });

      return true;
    } catch (caught) {
      setActionError(caught);

      return false;
    } finally {
      setPendingAction(undefined);
    }
  }

  async function rotate(apiKey: ApiKey) {
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction(`rotate-${apiKey.id}`);

    try {
      const result = await rotateApiKey(apiKey.id);
      const versionKey = staffApiKeyVersionsKey(accountId, apiKey.id);

      onSecretIssued({
        title: `New API Key secret for ${apiKey.name}`,
        description:
          "Update the external system now. The old credential stops working 24 hours after this rotation.",
        value: result.secret,
        afterConfirmed: () => {
          void listApiKeyVersions(apiKey.id)
            .then((freshVersions) =>
              mutateCache(versionKey, freshVersions, { revalidate: false }),
            )
            .catch(() => undefined);
        },
      });
      await mutateCache(
        versionKey,
        (current: unknown) => [
          result.version,
          ...(Array.isArray(current)
            ? current.filter(
                (version) =>
                  typeof version !== "object" ||
                  version === null ||
                  !("id" in version) ||
                  version.id !== result.version.id,
              )
            : []),
        ],
        { revalidate: false },
      );
    } catch (caught) {
      setActionError(caught);
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
            <Alert.Title>API Key request failed</Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(error)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <section className="flex flex-col gap-3">
        <div>
          <h3 className="text-xl font-semibold">Issue API Key</h3>
          <p className="text-sm text-muted">
            Access cannot be changed later. Issue a replacement when its
            permissions need to change.
          </p>
        </div>

        <form className="flex max-w-3xl flex-col gap-4" onSubmit={issue}>
          <FormTextField
            fullWidth
            isRequired
            isDisabled={Boolean(pendingAction)}
            label="API key name"
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
            <Label>Order access</Label>
            <Radio value="READ">
              <Radio.Content>
                <Radio.Control>
                  <Radio.Indicator />
                </Radio.Control>
                Read
              </Radio.Content>
            </Radio>
            <Radio value="WRITE">
              <Radio.Content>
                <Radio.Control>
                  <Radio.Indicator />
                </Radio.Control>
                Read and write
              </Radio.Content>
            </Radio>
          </RadioGroup>

          <div className="flex flex-col gap-3">
            <p className="text-sm font-medium">Locations</p>
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
            <Label>Expiration (optional)</Label>
            <Input />
          </TextField>

          <Button
            className="self-start"
            isDisabled={enabledLocations.length === 0}
            isPending={pendingAction === "issue"}
            type="submit"
          >
            {pendingAction === "issue" ? "Issuing…" : "Issue"}
          </Button>
        </form>
      </section>

      <section className="flex flex-col gap-3">
        <h3 className="text-xl font-semibold">API Keys</h3>

        {areApiKeysLoading ? (
          <Spinner aria-label="Loading API Keys" />
        ) : apiKeys.length === 0 ? (
          <p className="text-muted">No keys issued.</p>
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
                      apiKey.revokedAt
                        ? "text-sm text-danger"
                        : "text-sm font-medium text-accent"
                    }
                  >
                    {apiKey.revokedAt ? "Revoked" : "Active"}
                  </span>
                </div>

                <div>
                  <p className="text-sm font-medium">Order access</p>
                  <p className="text-sm text-muted">
                    {apiKeyAccessLabel(apiKey)}
                  </p>
                </div>

                <div>
                  <p className="text-sm font-medium">Locations</p>
                  <p className="break-all text-sm text-muted">
                    {apiKey.locationIds
                      .map(
                        (locationId) =>
                          locations.find(
                            (location) => location.id === locationId,
                          )?.name ?? "Unavailable location",
                      )
                      .join(", ")}
                  </p>
                </div>

                <div className="text-sm text-muted">
                  <p>Issued {formatIntegrationDateTime(apiKey.createdAt)}</p>
                  <p>
                    {apiKey.expiresAt
                      ? `Expires ${formatIntegrationDateTime(apiKey.expiresAt)}`
                      : "Does not expire"}
                  </p>
                  {apiKey.revokedAt && (
                    <p>Revoked {formatIntegrationDateTime(apiKey.revokedAt)}</p>
                  )}
                </div>

                <div className="flex flex-wrap gap-3">
                  <Button
                    variant="secondary"
                    onPress={() => setSelectedApiKeyId(apiKey.id)}
                  >
                    Versions
                  </Button>
                  <Button
                    isDisabled={Boolean(apiKey.revokedAt)}
                    isPending={pendingAction === `rotate-${apiKey.id}`}
                    variant="secondary"
                    onPress={() => rotate(apiKey)}
                  >
                    Rotate
                  </Button>
                  <Button
                    isDisabled={Boolean(apiKey.revokedAt)}
                    isPending={pendingAction === `revoke-${apiKey.id}`}
                    variant="danger"
                    onPress={() => {
                      if (!pendingAction) setApiKeyToRevoke(apiKey);
                    }}
                  >
                    Revoke
                  </Button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <PanelPopup
        className="sm:max-w-[420px]"
        isOpen={Boolean(apiKeyToRevoke)}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) setApiKeyToRevoke(undefined);
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon status="danger" />
          <PanelPopup.Heading>
            Revoke {apiKeyToRevoke?.name}?
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body>
          Every secret version for this key will stop authenticating
          immediately. This cannot be undone.
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button slot="close" variant="tertiary">
            Cancel
          </Button>
          <Button
            isPending={pendingAction === `revoke-${apiKeyToRevoke?.id ?? ""}`}
            variant="danger"
            onPress={() => {
              if (!apiKeyToRevoke) return;

              void revoke(apiKeyToRevoke).then((revoked) => {
                if (revoked) setApiKeyToRevoke(undefined);
              });
            }}
          >
            Revoke
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>

      <PanelPopup
        isOpen={Boolean(selectedApiKey)}
        size="md"
        onOpenChange={(open) => {
          if (!open) setSelectedApiKeyId(undefined);
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Heading>
            {selectedApiKey?.name} versions
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          <p className="text-sm text-muted">
            After rotation, the previous secret remains valid until the time
            shown below.
          </p>
          {versionsError ? (
            <Alert status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>Versions unavailable</Alert.Title>
                <Alert.Description>
                  {getIntegrationErrorMessage(versionsError)}
                </Alert.Description>
              </Alert.Content>
            </Alert>
          ) : areVersionsLoading ? (
            <div className="flex min-h-32 items-center justify-center">
              <Spinner aria-label="Loading API Key versions" />
            </div>
          ) : versions.length === 0 ? (
            <p className="text-muted">No versions found.</p>
          ) : (
            <div className="border-t border-separator">
              {versions.map((version) => (
                <div
                  key={version.id}
                  className="flex flex-wrap items-center justify-between gap-3 border-b border-separator py-3"
                >
                  <p className="text-sm text-muted">
                    Issued {formatIntegrationDateTime(version.issuedAt)}
                  </p>
                  <span
                    className={
                      version.retiredAt
                        ? "text-sm secondary-text"
                        : "text-sm font-medium text-accent"
                    }
                  >
                    {version.retiredAt
                      ? "Retired"
                      : version.validUntil
                        ? `Valid until ${formatIntegrationDateTime(
                            version.validUntil,
                          )}`
                        : "Current"}
                  </span>
                </div>
              ))}
            </div>
          )}
        </PanelPopup.Body>
      </PanelPopup>
    </div>
  );
}

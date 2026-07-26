import type { FormEvent } from "react";
import type { Location } from "@/src/api/locations";
import type { PendingOneTimeSecret } from "./one-time-secret";

import {
  Alert,
  Button,
  Chip,
  Input,
  Label,
  Radio,
  RadioGroup,
  Spinner,
  Surface,
  TextField,
} from "@heroui/react";
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";

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
  const [name, setName] = useState("");
  const [accessMode, setAccessMode] = useState<"READ" | "WRITE">("READ");
  const [selectedLocationIds, setSelectedLocationIds] = useState<string[]>([]);
  const [expiration, setExpiration] = useState("");
  const [selectedApiKeyId, setSelectedApiKeyId] = useState<string>();
  const [pendingAction, setPendingAction] = useState<string>();
  const [actionError, setActionError] = useState<unknown>();

  const apiKeysCacheKey = staffApiKeysKey(accountId, integrationId);
  const {
    data: apiKeys = [],
    error: apiKeysError,
    isLoading: areApiKeysLoading,
    isValidating: areApiKeysValidating,
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

  const error = apiKeysError ?? versionsError ?? actionError;

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
        title: `API Key issued for ${result.apiKey.name}`,
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

  async function revoke(apiKey: ApiKey) {
    if (pendingAction) return;

    setActionError(undefined);
    setPendingAction(`revoke-${apiKey.id}`);

    try {
      const revoked = await revokeApiKey(apiKey.id);

      await mutateApiKeys((current) => replaceApiKey(current, revoked), {
        revalidate: false,
      });
    } catch (caught) {
      setActionError(caught);
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
          "Deploy the new credential before its predecessor reaches the overlap deadline.",
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
      setSelectedApiKeyId(apiKey.id);
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
            <Alert.Title>API Key request failed</Alert.Title>
            <Alert.Description>
              {getIntegrationErrorMessage(error)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <section className="flex flex-col gap-4">
        <div>
          <h3 className="text-xl font-semibold">Issue API Key</h3>
          <p className="text-sm text-muted">
            Scope and location access are immutable. Issue a replacement Key to
            change either setting.
          </p>
        </div>

        <form className="flex max-w-3xl flex-col gap-4" onSubmit={issue}>
          <TextField
            fullWidth
            isRequired
            isDisabled={Boolean(pendingAction)}
            maxLength={64}
            name="api-key-name"
            value={name}
            onChange={setName}
          >
            <Label>Name</Label>
            <Input placeholder="Kitchen POS" />
          </TextField>

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

          <div className="flex flex-col gap-2">
            <p className="text-sm font-medium">Locations</p>
            <div className="flex flex-wrap gap-2">
              {locations.map((location) => (
                <Button
                  key={location.id}
                  aria-pressed={selectedLocationIds.includes(location.id)}
                  isDisabled={Boolean(pendingAction)}
                  size="sm"
                  variant={
                    selectedLocationIds.includes(location.id)
                      ? "primary"
                      : "secondary"
                  }
                  onPress={() => toggleLocation(location.id)}
                >
                  {location.id}
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
            isPending={pendingAction === "issue"}
            type="submit"
          >
            {pendingAction === "issue" ? "Issuing…" : "Issue API Key"}
          </Button>
        </form>
      </section>

      <section className="flex flex-col gap-4">
        <div>
          <h3 className="text-xl font-semibold">API Keys</h3>
          {areApiKeysValidating && (
            <p className="text-sm text-muted">Refreshing API Keys…</p>
          )}
        </div>

        {areApiKeysLoading ? (
          <Spinner aria-label="Loading API Keys" />
        ) : apiKeys.length === 0 ? (
          <p className="text-muted">No API Keys have been issued.</p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {apiKeys.map((apiKey) => (
              <Surface
                key={apiKey.id}
                className="flex flex-col gap-4 rounded-2xl p-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h4 className="font-semibold">{apiKey.name}</h4>
                    <p className="break-all text-xs text-muted">{apiKey.id}</p>
                  </div>
                  <Chip color={apiKey.revokedAt ? "danger" : "success"}>
                    {apiKey.revokedAt ? "Revoked" : "Active"}
                  </Chip>
                </div>

                <div className="flex flex-wrap gap-2">
                  {apiKey.scopes.map((scope) => (
                    <Chip key={scope}>{scope}</Chip>
                  ))}
                </div>

                <div>
                  <p className="text-sm font-medium">Locations</p>
                  <p className="break-all text-sm text-muted">
                    {apiKey.locationIds.join(", ")}
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

                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    onPress={() => setSelectedApiKeyId(apiKey.id)}
                  >
                    View versions
                  </Button>
                  <Button
                    isDisabled={Boolean(apiKey.revokedAt)}
                    isPending={pendingAction === `rotate-${apiKey.id}`}
                    size="sm"
                    variant="secondary"
                    onPress={() => rotate(apiKey)}
                  >
                    Rotate secret
                  </Button>
                  <Button
                    isDisabled={Boolean(apiKey.revokedAt)}
                    isPending={pendingAction === `revoke-${apiKey.id}`}
                    size="sm"
                    variant="danger"
                    onPress={() => revoke(apiKey)}
                  >
                    Revoke
                  </Button>
                </div>
              </Surface>
            ))}
          </div>
        )}
      </section>

      {selectedApiKey && (
        <section className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 className="text-xl font-semibold">
                {selectedApiKey.name} versions
              </h3>
              <p className="text-sm text-muted">
                Rotation keeps the prior version valid for a bounded overlap.
              </p>
            </div>
            <Button
              size="sm"
              variant="ghost"
              onPress={() => setSelectedApiKeyId(undefined)}
            >
              Close
            </Button>
          </div>

          {areVersionsLoading ? (
            <Spinner aria-label="Loading API Key versions" />
          ) : versions.length === 0 ? (
            <p className="text-muted">No versions found.</p>
          ) : (
            <div className="flex flex-col gap-2">
              {versions.map((version) => (
                <Surface
                  key={version.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl p-3"
                >
                  <div>
                    <p className="break-all font-mono text-xs">{version.id}</p>
                    <p className="text-sm text-muted">
                      Issued {formatIntegrationDateTime(version.issuedAt)}
                    </p>
                  </div>
                  <Chip color={version.retiredAt ? "default" : "success"}>
                    {version.retiredAt
                      ? "Retired"
                      : version.validUntil
                        ? `Valid until ${formatIntegrationDateTime(
                            version.validUntil,
                          )}`
                        : "Current"}
                  </Chip>
                </Surface>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}

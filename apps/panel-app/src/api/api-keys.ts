import { z } from "zod";

import { request } from "./api-fetch";
import { managedIntegrationNameSchema } from "./integrations";

export const apiKeyScopeSchema = z.enum(["orders:read", "orders:write"]);

export const apiKeyVersionSchema = z.object({
  id: z.uuid(),
  apiKeyId: z.uuid(),
  issuedAt: z.iso.datetime({ offset: true }),
  validUntil: z.iso.datetime({ offset: true }).nullable(),
  retiredAt: z.iso.datetime({ offset: true }).nullable(),
});

export const apiKeySchema = z.object({
  id: z.uuid(),
  integrationId: z.uuid(),
  tenantId: z.uuid(),
  name: z.string(),
  scopes: z.array(apiKeyScopeSchema),
  locationIds: z.array(z.uuid()),
  expiresAt: z.iso.datetime({ offset: true }).nullable(),
  revokedAt: z.iso.datetime({ offset: true }).nullable(),
  createdAt: z.iso.datetime({ offset: true }),
});

const apiKeysSchema = z.array(apiKeySchema);
const apiKeyVersionsSchema = z.array(apiKeyVersionSchema);

const issueApiKeyInputSchema = z.object({
  name: managedIntegrationNameSchema,
  scopes: z.array(apiKeyScopeSchema).min(1, "Choose an API Key scope"),
  locationIds: z.array(z.uuid()).min(1, "Choose at least one location"),
  expiresAt: z.iso.datetime({ offset: true }).nullable(),
});

const issuedApiKeySchema = z.object({
  apiKey: apiKeySchema,
  version: apiKeyVersionSchema,
  secret: z.string().min(1),
});

const issuedApiKeyVersionSchema = z.object({
  version: apiKeyVersionSchema,
  secret: z.string().min(1),
});

export type ApiKeyScope = z.infer<typeof apiKeyScopeSchema>;
export type ApiKeyVersion = z.infer<typeof apiKeyVersionSchema>;
export type ApiKey = z.infer<typeof apiKeySchema>;
export type IssueApiKeyInput = z.input<typeof issueApiKeyInputSchema>;
export type IssuedApiKey = z.infer<typeof issuedApiKeySchema>;
export type IssuedApiKeyVersion = z.infer<typeof issuedApiKeyVersionSchema>;

export function listApiKeys(integrationId: string): Promise<ApiKey[]> {
  const search = new URLSearchParams({ integrationId });

  return request(`/api/api-keys/v1?${search}`, apiKeysSchema);
}

export function issueApiKey(
  integrationId: string,
  input: IssueApiKeyInput,
): Promise<IssuedApiKey> {
  const validated = issueApiKeyInputSchema.parse(input);

  return request("/api/api-keys/v1", issuedApiKeySchema, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ integrationId, ...validated }),
  });
}

export function revokeApiKey(apiKeyId: string): Promise<ApiKey> {
  return request(`/api/api-keys/v1/${apiKeyId}/revocation`, apiKeySchema, {
    method: "PUT",
  });
}

export function listApiKeyVersions(apiKeyId: string): Promise<ApiKeyVersion[]> {
  const search = new URLSearchParams({ apiKeyId });

  return request(`/api/api-key-versions/v1?${search}`, apiKeyVersionsSchema);
}

export function rotateApiKey(apiKeyId: string): Promise<IssuedApiKeyVersion> {
  return request("/api/api-key-versions/v1", issuedApiKeyVersionSchema, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ apiKeyId }),
  });
}

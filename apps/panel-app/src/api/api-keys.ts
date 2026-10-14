import { z } from "zod";

import { apiFetch, request } from "./api-fetch";
import { managedIntegrationNameSchema } from "./integrations";

const apiKeyScopeSchema = z.enum(["orders:read", "orders:write"]);

const apiKeySchema = z.object({
  id: z.uuid(),
  integrationId: z.uuid(),
  tenantId: z.uuid(),
  name: z.string(),
  scopes: z.array(apiKeyScopeSchema),
  locationIds: z.array(z.uuid()),
  expiresAt: z.iso.datetime({ offset: true }).nullable(),
  lastUsedAt: z.iso.datetime({ offset: true }).nullable(),
  createdAt: z.iso.datetime({ offset: true }),
});

const apiKeysSchema = z.array(apiKeySchema);

const issueApiKeyInputSchema = z.object({
  name: managedIntegrationNameSchema,
  scopes: z.array(apiKeyScopeSchema).min(1, "Wybierz uprawnienia klucza API."),
  locationIds: z.array(z.uuid()).min(1, "Wybierz co najmniej jeden lokal."),
  expiresAt: z.iso.datetime({ offset: true }).nullable(),
});

const issuedApiKeySchema = z.object({
  apiKey: apiKeySchema,
  secret: z.string().min(1),
});

export type ApiKey = z.infer<typeof apiKeySchema>;
type IssueApiKeyInput = z.input<typeof issueApiKeyInputSchema>;
type IssuedApiKey = z.infer<typeof issuedApiKeySchema>;

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

export async function deleteApiKey(apiKeyId: string): Promise<void> {
  await apiFetch(`/api/api-keys/v1/${apiKeyId}`, {
    method: "DELETE",
  });
}

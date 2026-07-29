import { z } from "zod";

import { apiFetch, request } from "./api-fetch";

export const managedIntegrationNameSchema = z
  .string()
  .trim()
  .min(1, "Name is required")
  .refine(
    (name) => Array.from(name).length <= 64,
    "Name must contain at most 64 characters",
  )
  .refine(
    (name) => !/[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(name),
    "Use one line of text.",
  );

export const externalIntegrationStatusSchema = z.enum([
  "ENABLED",
  "DISABLED",
  "ARCHIVED",
]);

export const externalIntegrationSchema = z.object({
  id: z.uuid(),
  tenantId: z.uuid(),
  name: z.string(),
  status: externalIntegrationStatusSchema,
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

const externalIntegrationsSchema = z.array(externalIntegrationSchema);

export type ExternalIntegrationStatus = z.infer<
  typeof externalIntegrationStatusSchema
>;
export type ExternalIntegration = z.infer<typeof externalIntegrationSchema>;

export function listExternalIntegrations(): Promise<ExternalIntegration[]> {
  return request("/api/external-integrations/v1", externalIntegrationsSchema);
}

export function createExternalIntegration(
  name: string,
): Promise<ExternalIntegration> {
  const validatedName = managedIntegrationNameSchema.parse(name);

  return request("/api/external-integrations/v1", externalIntegrationSchema, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: validatedName }),
  });
}

export function renameExternalIntegration(
  integrationId: string,
  name: string,
): Promise<ExternalIntegration> {
  const validatedName = managedIntegrationNameSchema.parse(name);

  return request(
    `/api/external-integrations/v1/${integrationId}`,
    externalIntegrationSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: validatedName }),
    },
  );
}

export function updateExternalIntegrationStatus(
  integrationId: string,
  status: Extract<ExternalIntegrationStatus, "ENABLED" | "DISABLED">,
): Promise<ExternalIntegration> {
  return request(
    `/api/external-integrations/v1/${integrationId}/status`,
    externalIntegrationSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    },
  );
}

export async function archiveExternalIntegration(
  integrationId: string,
): Promise<void> {
  await apiFetch(`/api/external-integrations/v1/${integrationId}`, {
    method: "DELETE",
  });
}

import { z } from "zod";

import { apiFetch, request } from "./api-fetch";

const tenantRegistrationInvitationSchema = z.object({
  id: z.uuid(),
  label: z.string(),
  issuedByUsername: z.string(),
  createdAt: z.iso.datetime({ offset: true }),
  expiresAt: z.iso.datetime({ offset: true }),
});

const createdTenantRegistrationInvitationSchema =
  tenantRegistrationInvitationSchema.extend({
    invitationLink: z.url(),
  });

const tenantRegistrationInvitationsSchema = z.array(
  tenantRegistrationInvitationSchema,
);

const createTenantRegistrationInvitationInputSchema = z.object({
  label: z
    .string()
    .trim()
    .min(1, "Label is required")
    .max(120, "Label must not exceed 120 characters"),
});

export type TenantRegistrationInvitation = z.infer<
  typeof tenantRegistrationInvitationSchema
>;
export type CreatedTenantRegistrationInvitation = z.infer<
  typeof createdTenantRegistrationInvitationSchema
>;
export type CreateTenantRegistrationInvitationInput = z.input<
  typeof createTenantRegistrationInvitationInputSchema
>;

export function listTenantRegistrationInvitations(): Promise<
  TenantRegistrationInvitation[]
> {
  return request(
    "/api/tenant-registration-invitations/v1",
    tenantRegistrationInvitationsSchema,
  );
}

export function createTenantRegistrationInvitation(
  invitation: CreateTenantRegistrationInvitationInput,
): Promise<CreatedTenantRegistrationInvitation> {
  const input = createTenantRegistrationInvitationInputSchema.parse(invitation);

  return request(
    "/api/tenant-registration-invitations/v1",
    createdTenantRegistrationInvitationSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}

export async function revokeTenantRegistrationInvitation(
  invitationId: string,
): Promise<boolean> {
  await apiFetch(
    `/api/tenant-registration-invitations/v1/${encodeURIComponent(invitationId)}`,
    { method: "DELETE" },
  );

  return true;
}

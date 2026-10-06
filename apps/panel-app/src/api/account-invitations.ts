import { z } from "zod";

import { passwordInputSchema, requiredEmailInputSchema } from "./account-input";
import { assignmentRoleSchema } from "./accounts";
import { apiFetch, request } from "./api-fetch";
import { authenticationResultSchema } from "./authentication";

const invitationSchema = z.object({
  id: z.uuid(),
  locationId: z.uuid(),
  locationName: z.string(),
  role: assignmentRoleSchema,
  issuedByEmail: z.string(),
  createdAt: z.iso.datetime({ offset: true }),
  expiresAt: z.iso.datetime({ offset: true }),
});

const createdInvitationSchema = invitationSchema.extend({
  invitationLink: z.url(),
});

const invitationsSchema = z.array(invitationSchema);

const previewSchema = z.object({
  locationName: z.string(),
  role: assignmentRoleSchema,
  expiresAt: z.iso.datetime({ offset: true }),
});

const createInvitationInputSchema = z.object({
  locationId: z.uuid(),
  role: assignmentRoleSchema,
});

const redemptionInputSchema = z.object({
  token: z.string().min(1),
  email: requiredEmailInputSchema,
  password: passwordInputSchema,
  passwordConfirmation: z.string(),
});

export type AccountInvitation = z.infer<typeof invitationSchema>;
export type CreatedAccountInvitation = z.infer<typeof createdInvitationSchema>;
export type AccountInvitationPreview = z.infer<typeof previewSchema>;
export type CreateAccountInvitationInput = z.input<
  typeof createInvitationInputSchema
>;
export type RedeemAccountInvitationInput = z.input<
  typeof redemptionInputSchema
>;

export function listAccountInvitations(): Promise<AccountInvitation[]> {
  return request("/api/account-invitations/v1", invitationsSchema);
}

export function createAccountInvitation(
  invitation: CreateAccountInvitationInput,
): Promise<CreatedAccountInvitation> {
  const input = createInvitationInputSchema.parse(invitation);

  return request("/api/account-invitations/v1", createdInvitationSchema, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export async function revokeAccountInvitation(
  invitationId: string,
): Promise<boolean> {
  await apiFetch(
    `/api/account-invitations/v1/${encodeURIComponent(invitationId)}`,
    { method: "DELETE" },
  );

  return true;
}

export function previewAccountInvitation(
  token: string,
): Promise<AccountInvitationPreview> {
  return request(
    "/api/account-invitation-previews/v1",
    previewSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    },
    { notifyUnauthorized: false },
  );
}

export function redeemAccountInvitation(
  invitation: RedeemAccountInvitationInput,
) {
  const input = redemptionInputSchema.parse(invitation);

  return request(
    "/api/account-invitation-redemptions/v1",
    authenticationResultSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    { notifyUnauthorized: false },
  );
}

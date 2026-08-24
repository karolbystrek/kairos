import { z } from "zod";

import {
  passwordInputSchema,
  requiredEmailInputSchema,
  usernameInputSchema,
} from "./account-input";
import { request } from "./api-fetch";
import { currentAccountSchema, type CurrentAccount } from "./authentication";

const tenantRegistrationInputSchema = z
  .object({
    token: z.string().min(1, "Invitation token is required"),
    username: usernameInputSchema,
    email: requiredEmailInputSchema,
    password: passwordInputSchema,
    passwordConfirmation: z.string(),
  })
  .refine(
    ({ password, passwordConfirmation }) => password === passwordConfirmation,
    {
      message: "Passwords must match",
      path: ["passwordConfirmation"],
    },
  )
  .transform(({ token, username, email, password, passwordConfirmation }) => ({
    token,
    username,
    email,
    password,
    passwordConfirmation,
  }));

const tenantRegistrationInvitationPreviewSchema = z.object({
  expiresAt: z.iso.datetime({ offset: true }),
});

export type TenantRegistrationInput = z.input<
  typeof tenantRegistrationInputSchema
>;
export type TenantRegistrationInvitationPreview = z.infer<
  typeof tenantRegistrationInvitationPreviewSchema
>;

export function registerTenant(
  registration: TenantRegistrationInput,
): Promise<CurrentAccount> {
  const input = tenantRegistrationInputSchema.parse(registration);

  return request(
    "/api/tenant-registrations/v1",
    currentAccountSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    { retryUnauthorized: false },
  );
}

export function previewTenantRegistrationInvitation(
  token: string,
): Promise<TenantRegistrationInvitationPreview> {
  return request(
    "/api/tenant-registration-invitation-previews/v1",
    tenantRegistrationInvitationPreviewSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    },
    { retryUnauthorized: false },
  );
}

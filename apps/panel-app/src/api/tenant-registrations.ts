import { z } from "zod";

import {
  displayNameInputSchema,
  passwordInputSchema,
  requiredEmailInputSchema,
  usernameInputSchema,
} from "./account-input";
import { request } from "./api-fetch";

const tenantRegistrationInputSchema = z
  .object({
    tenantName: z
      .string()
      .trim()
      .min(1, "Tenant name is required")
      .max(120, "Tenant name must not exceed 120 characters"),
    locationName: z
      .string()
      .trim()
      .min(1, "Location name is required")
      .max(120, "Location name must not exceed 120 characters"),
    displayName: displayNameInputSchema,
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
  .transform(
    ({ tenantName, locationName, displayName, username, email, password }) => ({
      tenantName,
      locationName,
      administrator: {
        displayName,
        username,
        email,
        password,
      },
    }),
  );

const tenantRegistrationSchema = z.object({
  tenantId: z.uuid(),
  locationId: z.uuid(),
  administratorAccountId: z.uuid(),
  username: z.string(),
});

export type TenantRegistrationInput = z.input<
  typeof tenantRegistrationInputSchema
>;
export type TenantRegistration = z.infer<typeof tenantRegistrationSchema>;

export function registerTenant(
  registration: TenantRegistrationInput,
): Promise<TenantRegistration> {
  const input = tenantRegistrationInputSchema.parse(registration);

  return request(
    "/api/tenant-registrations",
    tenantRegistrationSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    { retryUnauthorized: false },
  );
}

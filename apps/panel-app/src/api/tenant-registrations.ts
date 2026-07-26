import { z } from "zod";

import {
  passwordInputSchema,
  requiredEmailInputSchema,
  usernameInputSchema,
} from "./account-input";
import { request } from "./api-fetch";

const tenantRegistrationInputSchema = z
  .object({
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
  .transform(({ username, email, password }) => ({
    administrator: {
      username,
      email,
      password,
    },
  }));

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
    "/api/tenant-registrations/v1",
    tenantRegistrationSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    { retryUnauthorized: false },
  );
}

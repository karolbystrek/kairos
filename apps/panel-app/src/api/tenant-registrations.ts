import { z } from "zod";

import { passwordInputSchema, requiredEmailInputSchema } from "./account-input";
import { request } from "./api-fetch";
import { authenticationResultSchema } from "./authentication";
export const registrationInputSchema = z
  .object({
    email: requiredEmailInputSchema,
    password: passwordInputSchema.min(12, "Use at least 12 characters."),
    passwordConfirmation: z.string(),
  })
  .refine(
    ({ password, passwordConfirmation }) => password === passwordConfirmation,
    { message: "Passwords must match", path: ["passwordConfirmation"] },
  );
export type TenantRegistrationInput = z.input<typeof registrationInputSchema>;
export function registerTenant(registration: TenantRegistrationInput) {
  return request(
    "/api/tenant-registrations/v1",
    authenticationResultSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(registrationInputSchema.parse(registration)),
    },
    { notifyUnauthorized: false },
  );
}

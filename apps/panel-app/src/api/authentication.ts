import { z } from "zod";

import { apiFetch, resetCsrf, request } from "./api-fetch";
import { requiredEmailInputSchema } from "./account-input";

export const currentAccountSchema = z.object({
  accountId: z.uuid(),
  email: z.string(),
  tenantId: z.uuid(),
  tenantRole: z.enum(["ADMIN", "MEMBER"]),
  assignment: z
    .object({ locationId: z.uuid(), role: z.enum(["MANAGER", "OPERATOR"]) })
    .nullable(),
  capabilities: z.array(z.string()),
});
export const authenticationResultSchema = currentAccountSchema;
export type CurrentAccount = z.infer<typeof currentAccountSchema>;
export type TenantAccount = CurrentAccount;
export type AuthenticationResult = z.infer<typeof authenticationResultSchema>;
const loginSchema = z.object({
  email: requiredEmailInputSchema,
  password: z.string().min(1).max(200),
});

export type LoginCredentials = z.input<typeof loginSchema>;
export function getCurrentAccount(): Promise<CurrentAccount> {
  return request("/api/auth/v1/me", currentAccountSchema);
}
export async function login(
  credentials: LoginCredentials,
): Promise<AuthenticationResult> {
  const result = await request(
    "/api/auth/v1/login",
    authenticationResultSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(loginSchema.parse(credentials)),
    },
    { notifyUnauthorized: false },
  );

  resetCsrf();

  return result;
}
export async function logout(everywhere = false): Promise<boolean> {
  await apiFetch(
    `/api/auth/v1/${everywhere ? "logout-all" : "logout"}`,
    { method: "POST" },
    { notifyUnauthorized: false },
  );
  resetCsrf();

  return true;
}
export async function changePassword(
  currentPassword: string,
  password: string,
  passwordConfirmation: string,
): Promise<void> {
  await apiFetch("/api/auth/v1/password", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentPassword, password, passwordConfirmation }),
  });
  resetCsrf();
}

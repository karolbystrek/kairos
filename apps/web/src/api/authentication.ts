import { z } from "zod";

import { ApiError, apiFetch, resetCsrf, request } from "./api-fetch";
import { requiredEmailInputSchema } from "./account-input";
import { setSessionHint } from "./session-hint";

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
const pendingVerificationSchema = z.object({
  verificationRequired: z.literal(true),
  email: z.string(),
  // z.iso is not yet initialised when the prerender bundle evaluates this module.
  resendAvailableAt: z
    .string()
    .refine((value) => !Number.isNaN(Date.parse(value))),
});

export type PendingVerification = z.infer<typeof pendingVerificationSchema>;
export const authenticationResultSchema = z.union([
  currentAccountSchema,
  pendingVerificationSchema,
]);
export const pendingVerificationKey = [
  "authentication",
  "verification",
] as const;
export async function getPendingVerification(): Promise<PendingVerification | null> {
  try {
    return await request(
      "/api/auth/v1/verification",
      pendingVerificationSchema,
      undefined,
      { notifyUnauthorized: false },
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}
export async function verifyEmail(code: string): Promise<CurrentAccount> {
  const result = await request(
    "/api/auth/v1/verification",
    currentAccountSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        code: z
          .string()
          .regex(/^\d{6}$/)
          .parse(code),
      }),
    },
    { notifyUnauthorized: false },
  );

  resetCsrf();

  return result;
}
export function resendVerification(): Promise<PendingVerification> {
  return request(
    "/api/auth/v1/verification/resend",
    pendingVerificationSchema,
    { method: "POST" },
    { notifyUnauthorized: false },
  );
}
export type CurrentAccount = z.infer<typeof currentAccountSchema>;
export type TenantAccount = CurrentAccount;
type AuthenticationResult = z.infer<typeof authenticationResultSchema>;
const loginSchema = z.object({
  email: requiredEmailInputSchema,
  password: z.string().min(1).max(200),
});

export type LoginCredentials = z.input<typeof loginSchema>;
export async function getCurrentAccount(): Promise<CurrentAccount> {
  try {
    const account = await request("/api/auth/v1/me", currentAccountSchema);

    setSessionHint(true);

    return account;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      setSessionHint(false);
    }
    throw error;
  }
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
export async function logout(): Promise<boolean> {
  await apiFetch(
    "/api/auth/v1/logout",
    { method: "POST" },
    { notifyUnauthorized: false },
  );
  resetCsrf();
  setSessionHint(false);

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

// The API answers the same way whether or not the email has an account.
export async function requestPasswordReset(email: string): Promise<void> {
  await apiFetch(
    "/api/auth/v1/password-reset",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: requiredEmailInputSchema.parse(email) }),
    },
    { notifyUnauthorized: false },
  );
}
export async function resetPassword(input: {
  email: string;
  code: string;
  password: string;
  passwordConfirmation: string;
}): Promise<void> {
  await apiFetch(
    "/api/auth/v1/password-reset/confirmation",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    { notifyUnauthorized: false },
  );
  resetCsrf();
}

import { z } from "zod";

import {
  apiFetchWhileAuthLocked,
  initializeCsrf,
  request,
  requestWhileAuthLocked,
} from "./api-fetch";
import { withAuthCookieLock } from "./auth-coordination";

const locationAssignmentSchema = z.object({
  locationId: z.uuid(),
  role: z.enum(["MANAGER", "OPERATOR"]),
});

export const currentAccountSchema = z.object({
  accountId: z.uuid(),
  username: z.string(),
  tenantId: z.uuid(),
  tenantRole: z.enum(["ADMIN", "MEMBER"]),
  assignment: locationAssignmentSchema.nullable(),
  capabilities: z.array(z.string()),
});

const loginCredentialsSchema = z.object({
  username: z
    .string()
    .trim()
    .min(1, "Username is required")
    .max(120, "Username must not exceed 120 characters")
    .transform((username) => username.toLowerCase()),
  password: z
    .string()
    .min(1, "Password is required")
    .max(256, "Password is too long")
    .refine(
      (password) => new TextEncoder().encode(password).length <= 72,
      "Password is too long",
    ),
});

export type CurrentAccount = z.infer<typeof currentAccountSchema>;
export type LoginCredentials = z.input<typeof loginCredentialsSchema>;

export function getCurrentAccount(): Promise<CurrentAccount> {
  return request("/api/auth/v1/me", currentAccountSchema);
}

export async function login(
  credentials: LoginCredentials,
): Promise<CurrentAccount> {
  const input = loginCredentialsSchema.parse(credentials);

  await initializeCsrf();

  return withAuthCookieLock(() =>
    requestWhileAuthLocked(
      "/api/auth/v1/login",
      currentAccountSchema,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(input),
      },
      { retryUnauthorized: false },
    ),
  );
}

export async function logout(): Promise<boolean> {
  await initializeCsrf();

  await withAuthCookieLock(async () => {
    await apiFetchWhileAuthLocked("/api/auth/v1/logout", { method: "POST" });
  });

  return true;
}

export async function logoutAll(): Promise<boolean> {
  await initializeCsrf();

  await withAuthCookieLock(async () => {
    await apiFetchWhileAuthLocked("/api/auth/v1/logout-all", {
      method: "POST",
    });
  });

  return true;
}

import { z } from "zod";

import { apiFetch, request } from "./api-fetch";

export const assignmentRoleSchema = z.enum(["MANAGER", "OPERATOR"]);

const managedAccountSchema = z.object({
  id: z.uuid(),
  tenantId: z.uuid(),
  locationId: z.uuid(),
  username: z.string(),
  email: z.string(),
  role: assignmentRoleSchema,
  status: z.enum(["ENABLED", "DISABLED"]),
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

const managedAccountsSchema = z.array(managedAccountSchema);

export type AssignmentRole = z.infer<typeof assignmentRoleSchema>;
export type ManagedAccount = z.infer<typeof managedAccountSchema>;

export function listManagedAccounts(): Promise<ManagedAccount[]> {
  return request("/api/accounts/v1", managedAccountsSchema);
}

export function updateManagedAccountStatus(
  accountId: string,
  status: "ENABLED" | "DISABLED",
): Promise<ManagedAccount> {
  return request(
    `/api/accounts/v1/${encodeURIComponent(accountId)}/status`,
    managedAccountSchema,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    },
  );
}

export async function deleteManagedAccount(
  accountId: string,
): Promise<boolean> {
  await apiFetch(`/api/accounts/v1/${encodeURIComponent(accountId)}`, {
    method: "DELETE",
  });

  return true;
}

import { z } from "zod";

import {
  optionalEmailInputSchema,
  passwordInputSchema,
  usernameInputSchema,
} from "./account-input";
import { request } from "./api-fetch";

export const assignmentRoleSchema = z.enum(["MANAGER", "OPERATOR"]);

const provisionAccountInputSchema = z.object({
  username: usernameInputSchema,
  email: optionalEmailInputSchema,
  password: passwordInputSchema,
  role: assignmentRoleSchema,
});

const managedAccountSchema = z.object({
  id: z.uuid(),
  tenantId: z.uuid(),
  locationId: z.uuid(),
  username: z.string(),
  email: z.string().nullable(),
  role: assignmentRoleSchema,
  status: z.enum(["ACTIVE", "DISABLED"]),
  createdAt: z.iso.datetime({ offset: true }),
  updatedAt: z.iso.datetime({ offset: true }),
});

export type AssignmentRole = z.infer<typeof assignmentRoleSchema>;
export type ProvisionAccountInput = z.input<typeof provisionAccountInputSchema>;
export type ManagedAccount = z.infer<typeof managedAccountSchema>;

export function provisionAccount(
  locationId: string,
  account: ProvisionAccountInput,
): Promise<ManagedAccount> {
  const input = provisionAccountInputSchema.parse(account);

  return request(
    `/api/locations/${encodeURIComponent(locationId)}/accounts`,
    managedAccountSchema,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}

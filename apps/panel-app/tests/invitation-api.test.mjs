import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
function client() {
  const requests = [];
  const exports = {};
  const source = ts.transpileModule(readFileSync(new URL("../src/api/account-invitations.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
  new Function("require", "exports", source)(name => {
    if (name === "./api-fetch") return { request: async (url, schema, options) => {
      requests.push({ url, body: options?.body && JSON.parse(options.body) });
      return schema.parse(options?.body ? invitation : [invitation]);
    } };
    if (name === "./accounts") return { assignmentRoleSchema: require("zod").z.enum(["MANAGER", "OPERATOR"]) };
    if (name === "./authentication") return { currentAccountSchema: require("zod").z.object({}) };
    if (name === "./account-input") return { requiredEmailInputSchema: require("zod").z.email(), passwordInputSchema: require("zod").z.string() };
    return require(name);
  }, exports);
  return { api: exports, requests };
}
const invitation = {
  id: "00000000-0000-4000-8000-000000000001", locationId: "00000000-0000-4000-8000-000000000002", email: "invitee@example.com", locationName: "Restaurant", role: "OPERATOR", issuedByEmail: "owner@example.com", createdAt: "2026-10-12T12:00:00Z", expiresAt: "2026-10-19T12:00:00Z", state: "PENDING", deliveryState: "QUEUED",
};
test("invitation history requests the complete array without query parameters", async () => {
  const { api, requests } = client();
  const result = await api.listAccountInvitations();
  assert.equal(requests[0].url, "/api/account-invitations/v1");
  assert.deepEqual(result, [invitation]);
});
test("creating an invitation requires explicit opt-in to replacement and receives no bearer secret", async () => {
  const { api, requests } = client();
  const result = await api.createAccountInvitation({ email: invitation.email, locationId: invitation.locationId, role: "OPERATOR" });
  assert.equal(requests[0].body.replaceExisting, false);
  assert.equal("invitationLink" in result, false);
  assert.equal("token" in result, false);
  await api.createAccountInvitation({ email: invitation.email, locationId: invitation.locationId, role: "OPERATOR", replaceExisting: true });
  assert.equal(requests[1].body.replaceExisting, true);
});
test("resending explicitly opts into token replacement", async () => {
  const { api, requests } = client();
  await api.resendAccountInvitation(invitation.id);
  assert.equal(requests[0].url, `/api/account-invitations/v1/${invitation.id}/resend`);
  assert.deepEqual(requests[0].body, { replaceExisting: true });
});

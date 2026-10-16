import { beforeEach, expect, it } from "vitest";

import { csrf, stubFetch } from "./helpers";

import {
  createAccountInvitation,
  listAccountInvitations,
  resendAccountInvitation,
} from "@/src/api/account-invitations";

const invitation = {
  id: "00000000-0000-4000-8000-000000000001", locationId: "00000000-0000-4000-8000-000000000002",
  email: "invitee@example.com", locationName: "Restaurant", role: "OPERATOR",
  issuedByEmail: "owner@example.com", createdAt: "2026-10-12T12:00:00Z",
  expiresAt: "2026-10-19T12:00:00Z", state: "PENDING", deliveryState: "QUEUED",
};
let calls: ReturnType<typeof stubFetch>;

beforeEach(() => {
  calls = stubFetch((url, init) => {
    if (csrf(url)) return Response.json({ token: "csrf" });

    return Response.json(init.method === "POST" ? invitation : [invitation]);
  });
});

function requests() {
  return calls.filter(({ url }) => !csrf(url))
    .map(({ url, init }) => ({ url, body: init.body && JSON.parse(String(init.body)) }));
}

it("invitation history requests the complete array without query parameters", async () => {
  expect(await listAccountInvitations()).toEqual([invitation]);
  expect(requests()[0].url.href).toBe("https://api.example.test/api/account-invitations/v1");
});

it("creating an invitation requires explicit opt-in to replacement and receives no bearer secret", async () => {
  const result = await createAccountInvitation({ email: invitation.email, locationId: invitation.locationId, role: "OPERATOR" });

  expect(requests()[0].body).toMatchObject({ replaceExisting: false });
  expect(result).not.toHaveProperty("invitationLink");
  expect(result).not.toHaveProperty("token");
  await createAccountInvitation({ email: invitation.email, locationId: invitation.locationId, role: "OPERATOR", replaceExisting: true });
  expect(requests()[1].body).toMatchObject({ replaceExisting: true });
});

it("resending explicitly opts into token replacement", async () => {
  await resendAccountInvitation(invitation.id);
  expect(requests()[0].url.pathname).toBe(`/api/account-invitations/v1/${invitation.id}/resend`);
  expect(requests()[0].body).toEqual({ replaceExisting: true });
});

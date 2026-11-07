import { beforeEach, expect, it, vi } from "vitest";

import { clearInvitationFragment, handleInvitationProblem } from "@/components/registration-form";
import { ApiError } from "@/src/api/api-fetch";

const hashChanges = vi.fn();

beforeEach(() => {
  window.history.replaceState({ previous: "orders" }, "", "/account-registration?source=team#invitation=secret&keep=yes");
  hashChanges.mockReset();
  window.addEventListener("hashchange", hashChanges);

  return () => window.removeEventListener("hashchange", hashChanges);
});

const keptUrl = `${window.location.origin}/account-registration?source=team#keep=yes`;

it.each([
  ["expired", 410, /wygasło/i],
  ["revoked", 410, /cofnięte/i],
  ["redeemed", 410, /już wykorzystane/i],
  ["invalid", 404, /nieprawidłowe/i],
])("%s invitations remove their bearer fragment and give safe guidance", (reason, status, guidance) => {
  const message = handleInvitationProblem(new ApiError(status, {
    type: `urn:kairos:problem:account-invitation-${reason}`, detail: "secret provider detail",
  }));

  expect(message).toMatch(guidance);
  expect(message).not.toMatch(/secret|provider/);
  expect(window.location.href).toBe(keptUrl);
  expect(window.history.state).toEqual({ previous: "orders" });
  expect(hashChanges).toHaveBeenCalledOnce();
});

it.each([400, 403, 409, 503])("retryable registration failure %s preserves the invitation", (status) => {
  expect(handleInvitationProblem(new ApiError(status))).toBeUndefined();
  expect(window.location.hash).toBe("#invitation=secret&keep=yes");
  expect(hashChanges).not.toHaveBeenCalled();
});

it("clearing the invitation preserves unrelated URL state", () => {
  clearInvitationFragment();
  expect(window.location.href).toBe(keptUrl);
});

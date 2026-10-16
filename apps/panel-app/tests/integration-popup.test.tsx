import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";

import { apiCalls, deferred, fakeApi, location, renderWithSWR } from "./helpers";

import { IntegrationManagement } from "@/components/integration-management";

const ids = {
  tenant: "00000000-0000-4000-8000-0000000000b1",
  integration: "00000000-0000-4000-8000-000000000010",
  location: "00000000-0000-4000-8000-000000000001",
  webhook: "00000000-0000-4000-8000-000000000020",
};
const timestamps = { createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" };
const integration = { id: ids.integration, tenantId: ids.tenant, name: "Integration", status: "ENABLED", ...timestamps };
const subscription = {
  id: ids.webhook, integrationId: ids.integration, name: "Webhook", destinationUrl: "https://example.com/hook",
  status: "DISABLED", locationIds: [], eventTypes: [], signingSecretVersions: [], ...timestamps,
};
let pending: ReturnType<typeof deferred<unknown>>[];
let calls: ReturnType<typeof fakeApi>;

function hold() {
  const request = deferred();

  pending.push(request);

  return request.promise;
}

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  pending = [];
  calls = fakeApi({
    "GET /api/external-integrations/v1": () => [integration],
    "GET /api/locations/v1": () => [location(ids.location)],
    "GET /api/api-keys/v1": () => [],
    "GET /api/webhook-subscriptions/v1": () => [subscription],
    "POST /api/api-keys/v1": hold,
    "POST /api/webhook-signing-secrets/v1": hold,
  });
});

const secretRequests = () => apiCalls(calls).filter((call) => call === "POST /api/api-keys/v1" || call === "POST /api/webhook-signing-secrets/v1");
const issued = (index: number, secret: string) => ({
  apiKey: {
    id: `00000000-0000-4000-8000-00000000003${index}`, integrationId: ids.integration, tenantId: ids.tenant,
    name: `Key ${index}`, scopes: ["orders:read"], locationIds: [ids.location], expiresAt: null, lastUsedAt: null, createdAt: timestamps.createdAt,
  },
  secret,
});
const rotated = (index: number, secret: string) => ({
  version: { id: `00000000-0000-4000-8000-00000000004${index}`, issuedAt: timestamps.createdAt, validUntil: null, retiredAt: null },
  signingSecret: secret,
});

function setup(accountId = "staff") {
  const view = renderWithSWR(<IntegrationManagement key={accountId} accountId={accountId} />);
  const user = userEvent.setup();

  return {
    ...view,
    switchAccount: (next: string) => view.rerender(<IntegrationManagement key={next} accountId={next} />),
    async open() {
      await user.click(await screen.findByRole("button", { name: "Pokaż integrację Integration, włączona" }));

      return screen.findByRole("dialog", { name: "Integracja Integration" });
    },
    async dismiss() {
      await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Zamknij" }));
      await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    },
    /** Tries to issue an API key; returns whether the submit control was available. */
    async issue() {
      const dialog = screen.getByRole("dialog");
      const submit = within(dialog).getByRole("button", { name: "Utwórz" });

      if (submit.hasAttribute("disabled")) return false;
      await user.type(within(dialog).getByRole("textbox", { name: "Nazwa klucza API" }), "Key");
      await user.click(within(dialog).getByRole("button", { name: "Location" }));
      await user.click(submit);

      return true;
    },
    /** Tries to rotate the webhook signing secret; returns whether the control was available. */
    async rotate() {
      const dialog = screen.getByRole("dialog");

      await user.click(within(dialog).getByRole("tab", { name: "Webhooki" }));
      const rotate = await within(dialog).findByRole("button", { name: "Zmień sekret" });

      if (rotate.hasAttribute("disabled")) return false;
      await user.click(rotate);

      return true;
    },
    secret: () => (screen.queryByRole("textbox", { name: "Sekret" }) as HTMLInputElement | null)?.value,
    confirmSecret: () => user.click(screen.getByRole("button", { name: "Zapisano sekret" })),
  };
}

it.each([
  ["issue", issued, "rotate"],
  ["rotate", rotated, "issue"],
] as const)("%s: dismissal and reopening preserve ownership and the secret until confirmation", async (operation, result, other) => {
  const view = setup();

  await view.open();
  expect(await view[operation]()).toBe(true);
  await view.dismiss();
  await view.open();
  expect(await view[operation]()).toBe(false);
  // The other secret-producing operation shares ownership, even across tabs.
  expect(await view[other]()).toBe(false);
  expect(secretRequests()).toHaveLength(1);
  await view.dismiss();
  pending[0].resolve(result(1, "once"));
  await waitFor(() => expect(view.secret()).toBe("once"));
  await view.confirmSecret();
  await view.open();
  expect(await view[operation]()).toBe(true);
  expect(secretRequests()).toHaveLength(2);
  pending[1].resolve(result(2, "next"));
  await waitFor(() => expect(view.secret()).toBe("next"));
  view.switchAccount("other");
  expect(view.secret()).toBeUndefined();
});

it("a late secret from the previous account cannot enter the current account", async () => {
  const view = setup();

  await view.open();
  await view.issue();
  view.switchAccount("other");
  await view.open();
  await view.issue();
  pending[0].resolve(issued(1, "old-secret"));
  await new Promise((resolve) => setTimeout(resolve, 50));
  expect(view.secret()).toBeUndefined();
  pending[1].resolve(issued(2, "new-secret"));
  await waitFor(() => expect(view.secret()).toBe("new-secret"));
});

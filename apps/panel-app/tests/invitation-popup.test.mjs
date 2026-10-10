import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

function workspace(kind = "account-management", collections = {}) {
  const state = [];
  let cursor = 0;
  let pending = false;
  let complete;
  const requests = [];
  const exports = {};
  const dependencies = name => {
    if (name === "react") return {
      useEffect() {},
      useMemo: compute => compute(),
      useState(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = typeof initial === "function" ? initial() : initial;
        return [state[index], value => { state[index] = value; }];
      },
    };
    if (name === "@heroui/react") return new Proxy({}, { get: (_, key) => new Proxy(() => {}, { get: (_, part) => `${String(key)}.${String(part)}` }) });
    if (name === "swr") return { useSWRConfig: () => ({ mutate: async () => {} }), default: key => ({
      data: key === "locations" ? (collections.locations ?? [{ id: "location", status: "ENABLED", name: "Location" }, { id: "other", status: "ENABLED", name: "Other" }]) : (collections[key] ?? []),
      error: collections.errors?.[key],
      isLoading: collections.loading?.[key] ?? false,
      mutate: async () => { requests.push({ retry: key }); },
    }) };
    if (name === "swr/mutation") return { default: (key, mutation) => ({
      isMutating: ["createInvitationMutation", "createOrderMutation"].includes(mutation.name) && pending,
      reset() { if (["createInvitationMutation", "createOrderMutation"].includes(mutation.name)) pending = false; },
      async trigger(arg) {
        pending = true;
        const result = await mutation(key, { arg });
        pending = false;
        return result;
      },
    }) };
    if (name === "@/src/api/cache-keys") return {
      staffCachePrefix: "staff", staffLocationsKey: () => "locations", staffAccountsKey: () => "accounts",
      staffInvitationsKey: () => "invitations",
    };
    if (name === "@/src/api/account-invitations") return {
      createAccountInvitation(input) { requests.push(input); return new Promise(resolve => { complete = resolve; }); },
    };
    if (name === "@/src/api/orders") return {
      createOrderInputSchema: { safeParse: data => ({ success: true, data }) },
      createOrder(locationId) { requests.push(locationId); return new Promise(resolve => { complete = resolve; }); },
    };
    if (name === "@/src/api/account-input") {
      const inputExports = {};
      const inputSource = ts.transpileModule(readFileSync(new URL("../src/api/account-input.ts", import.meta.url), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
      new Function("require", "exports", inputSource)(require, inputExports);
      return inputExports;
    }
    if (name === "@/src/api/api-fetch") return { ApiError: class ApiError extends Error {} };
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  };
  const source = ts.transpileModule(readFileSync(new URL(`../components/${kind}.tsx`, import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  new Function("require", "exports", source)(dependencies, exports);
  function elements(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(elements);
    return [node, ...elements(node.props?.children), ...elements(node.props?.trailingActions), ...elements(node.props?.titleEditor)];
  }
  function render() {
    cursor = 0;
    if (kind === "account-management") return exports.AccountManagement({ account: { accountId: "staff", tenantRole: "ADMIN" } });
    if (kind === "location-management") return exports.LocationManagement({ accountId: "staff", onViewOrders() {} });
    return exports.OrderManagement({ accountId: "staff", canViewTenantOrders: true, canManageLocations: false });
  }
  function find(predicate) {
    cursor = 0;
    const tree = render();
    const node = elements(tree).find(predicate);
    assert.ok(node, "Expected invitation control to be available");
    return node.props;
  }
  return {
    requests,
    nodes: () => elements(render()),
    email: () => find(node => node.props?.name === "invitation-email"),
    open: () => find(node => node.props?.["aria-label"] === (kind === "account-management" ? "Nowe konto" : "Nowe zamówienie")).onPress(),
    popup: () => find(node => node.type === "PanelPopup" && node.props.size === "lg"),
    submit: () => find(node => node.type === "form").onSubmit({ preventDefault() {} }),
    filter: () => find(node => node.props?.label === "Lokal kolejki"),
    secret: () => find(node => node.type === "OneTimeSecret"),
    complete: result => complete(result),
  };
}

test("dismiss and reopen preserves pending creation and the unacknowledged invitation link", async () => {
  const view = workspace();
  view.open();
  view.email().onChange("invitee@example.com");
  const submission = view.submit();
  view.popup().onOpenChange(false);
  view.open();
  void view.submit();
  assert.equal(view.requests.length, 1);
  assert.equal(view.requests[0].email, "invitee@example.com");
  view.popup().onOpenChange(false);
  view.complete({ id: "invitation", role: "OPERATOR", locationName: "Location", invitationLink: "https://panel.example/invitation#secret" });
  await submission;
  assert.equal(view.popup().isOpen, true);
  const link = view.secret().secret.value;
  view.popup().onOpenChange(false);
  view.open();
  assert.equal(view.secret().secret.value, link);
  view.secret().onConfirmed();
  assert.equal(view.popup().isOpen, false);
});


test("dismiss and reopen cannot duplicate order creation by switching the queue filter", async () => {
  const view = workspace("order-management");
  view.open();
  const submission = view.submit();
  view.popup().onOpenChange(false);
  assert.equal(view.filter().isDisabled, true);
  const selected = view.filter().selectedId;
  view.filter().onChange("other");
  assert.equal(view.filter().selectedId, selected);
  view.open();
  void view.submit();
  assert.equal(view.requests.length, 1);
  view.complete({ id: "order", locationId: "location", label: "1", status: "IN_PREPARATION" });
  await submission;
});


test("account and invitation cards open only their details, and dismissal returns to the collection", () => {
  const view = workspace("account-management", {
    accounts: [{ id: "member", email: "member@example.com", role: "OPERATOR", locationId: "location", status: "ENABLED", createdAt: "2026-01-01T00:00:00Z" }],
    invitations: [{ id: "invite", email: "invitee@example.com", role: "MANAGER", locationName: "Location", issuedByEmail: "owner@example.com", createdAt: "2026-01-01T00:00:00Z", expiresAt: "2099-01-01T00:00:00Z" }],
  });
  const details = () => view.nodes().filter(node => node.type === "PanelPopup" && node.props["aria-label"]);
  const cards = () => view.nodes().filter(node => node.type === "PanelCard");
  assert.equal(cards().length, 2);
  assert.equal(details().length, 0);
  cards().find(node => node.props.title === "invitee@example.com").props.onPress();
  assert.equal(details().length, 1);
  assert.match(details()[0].props["aria-label"], /invitee@example.com/);
  details()[0].props.onOpenChange(false);
  assert.equal(details().length, 0);
  cards().find(node => node.props.title === "member@example.com").props.onPress();
  assert.equal(details().length, 1);
  assert.match(details()[0].props["aria-label"], /member@example.com/);
  details()[0].props.onOpenChange(false);
  assert.equal(details().length, 0);
});


test("invitation loading and failure preserve loaded accounts and offer invitation retry", async () => {
  const account = { id: "member", email: "member@example.com", role: "OPERATOR", locationId: "location", status: "ENABLED", createdAt: "2026-01-01T00:00:00Z" };
  const loading = workspace("account-management", { accounts: [account], loading: { invitations: true } });
  assert.equal(loading.nodes().filter(node => node.type === "PanelCard").length, 1);
  assert.ok(loading.nodes().some(node => node.props?.["aria-label"] === "Wczytywanie zaproszeń"));
  const failed = workspace("account-management", { accounts: [account], errors: { invitations: new Error("Unavailable") } });
  assert.equal(failed.nodes().filter(node => node.type === "PanelCard").length, 1);
  const retry = failed.nodes().find(node => node.props?.children === "Spróbuj ponownie");
  assert.ok(retry);
  retry.props.onPress();
  assert.deepEqual(failed.requests, [{ retry: "invitations" }]);
});


test("dismissed location drafts cannot carry into a newly created location", () => {
  const locations = [{ id: "a", name: "Location A", status: "ENABLED", googleReviewUrl: "https://example.com/a" }];
  const view = workspace("location-management", { locations });
  const find = predicate => { const node = view.nodes().find(predicate); assert.ok(node); return node.props; };
  find(node => node.type === "PanelCard").onPress();
  find(node => node.props?.["aria-label"] === "Edytuj nazwę lokalu").onPress();
  find(node => node.props?.name === "location-name").onChange("Draft A");
  find(node => node.props?.name === "google-review-url").onChange("https://example.com/draft-a");
  find(node => node.type === "PanelPopup" && node.props?.["aria-label"]).onOpenChange(false);
  const created = { id: "b", name: "Location B", status: "ENABLED", googleReviewUrl: "https://example.com/b" };
  locations.push(created);
  find(node => node.type === "LocationCreationModal").onCreated(created);
  assert.equal(find(node => node.type === "PanelDetailHeader").title, "Location B");
  assert.equal(find(node => node.type === "PanelDetailHeader").titleEditor, undefined);
  assert.equal(find(node => node.props?.name === "google-review-url").value, "https://example.com/b");
});


test("invitation creation rejects invalid email with a field error before issuing a link", async () => {
  const view = workspace();
  view.open();
  view.email().onChange("invalid");
  await view.submit();
  assert.equal(view.requests.length, 0);
  assert.ok(view.email().errorMessage);
  view.email().onChange("invitee@example.com");
  assert.equal(view.email().errorMessage, undefined);
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

function workspace(kind = "account-management") {
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
    if (name === "swr") return { default: key => ({
      data: key === "locations" ? [{ id: "location", status: "ENABLED", name: "Location" }, { id: "other", status: "ENABLED", name: "Other" }] : [],
      mutate: async () => {},
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
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  };
  const source = ts.transpileModule(readFileSync(new URL(`../components/${kind}.tsx`, import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  new Function("require", "exports", source)(dependencies, exports);
  function elements(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(elements);
    return [node, ...elements(node.props?.children)];
  }
  function find(predicate) {
    cursor = 0;
    const tree = kind === "account-management"
      ? exports.AccountManagement({ account: { accountId: "staff", tenantRole: "ADMIN" } })
      : exports.OrderManagement({ accountId: "staff", canViewTenantOrders: true, canManageLocations: false });
    const node = elements(tree).find(predicate);
    assert.ok(node, "Expected invitation control to be available");
    return node.props;
  }
  return {
    requests,
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
  const submission = view.submit();
  view.popup().onOpenChange(false);
  view.open();
  void view.submit();
  assert.equal(view.requests.length, 1);
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

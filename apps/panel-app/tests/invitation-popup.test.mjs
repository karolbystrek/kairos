import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

// Loads a real source module, resolving its imports through the test's stubs.
function loadModule(path, resolve) {
  const exports = {};
  const source = ts.transpileModule(readFileSync(new URL(`../${path}.ts`, import.meta.url), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  new Function("require", "exports", source)(name => resolve(name.startsWith("./") ? `@/src/api/${name.slice(2)}` : name), exports);
  return exports;
}

function workspace(kind = "account-management", collections = {}) {
  const state = [];
  let cursor = 0;
  let complete;
  class ApiError extends Error { constructor(status) { super(); this.status = status; } }
  const requests = [];
  const exports = {};
  const dependencies = name => {
    if (name === "react") return {
      useEffect() {},
      useMemo: compute => compute(),
      useRef(initial) { const index = cursor++; return state[index] ??= { current: initial }; },
      useState(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = typeof initial === "function" ? initial() : initial;
        return [state[index], value => { state[index] = value; }];
      },
    };
    if (name === "@heroui/react") return new Proxy({}, { get: (_, key) => new Proxy(() => {}, { get: (_, part) => key === "toast" ? () => {} : `${String(key)}.${String(part)}` }) });
    if (name === "swr") return { useSWRConfig: () => ({ mutate: async () => {} }), default: key => ({
      data: key === "locations" ? (collections.locations ?? [{ id: "location", status: "ENABLED", name: "Location" }, { id: "other", status: "ENABLED", name: "Other" }]) : key === "invitations" ? (collections.invitations ?? []).map(invitation => ({ state: "PENDING", deliveryState: "QUEUED", ...invitation })) : (key in collections ? collections[key] : []),
      error: collections.errors?.[key],
      isLoading: collections.loading?.[key] ?? false,
      mutate: async () => { requests.push({ retry: key }); },
    }) };
    // Each mutation hook keeps its own pending and error state, like SWR's.
    if (name === "swr/mutation") return { default: (key, mutation) => {
      const hook = state[cursor++] ??= {};
      return {
        isMutating: Boolean(hook.pending),
        error: hook.error,
        reset() { hook.pending = false; hook.error = undefined; },
        async trigger(arg) {
          hook.pending = true;
          try { return await mutation(key, { arg }); } catch (error) { hook.error = error; throw error; } finally { hook.pending = false; }
        },
      };
    } };
    if (name === "@/src/api/cache-keys") return {
      staffCachePrefix: "staff", staffLocationsKey: () => "locations", staffAccountsKey: () => "accounts",
      staffInvitationsKey: () => "invitations", staffLocationMenuKey: () => "location-menu",
    };
    if (name === "@/src/api/account-invitations") return {
      createAccountInvitation(input) { requests.push(input); if (collections.conflict && !input.replaceExisting) throw new ApiError(409); return new Promise(resolve => { complete = resolve; }); },
      async resendAccountInvitation(id) { requests.push({ resend: id }); return { id: "replacement", email: "invitee@example.com", role: "OPERATOR", locationName: "Location" }; },
      async revokeAccountInvitation(id) { requests.push({ revoke: id }); return true; },
    };
    if (name === "@/src/api/orders") return {
      createOrderInputSchema: { safeParse: data => ({ success: true, data }) },
      describeOrderItems: () => "",
      createOrder(locationId) { requests.push(locationId); return new Promise(resolve => { complete = resolve; }); },
    };
    if (name === "@/src/api/account-input") {
      const inputExports = {};
      const inputSource = ts.transpileModule(readFileSync(new URL("../src/api/account-input.ts", import.meta.url), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
      new Function("require", "exports", inputSource)(require, inputExports);
      return inputExports;
    }
    if (["@/components/loading-indicator", "@/components/status-chip"].includes(name)) {
      const loadingExports = {};
      const loadingSource = ts.transpileModule(readFileSync(new URL(`../${name.slice(2)}.tsx`, import.meta.url), "utf8"),
        { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
      new Function("require", "exports", loadingSource)(dependencies, loadingExports);
      return loadingExports;
    }
    if (name === "@/components/order-management") return { useMinuteClock: () => Date.now() };
    if (name === "@/src/api/locations" && kind === "location-creation-modal") return {
      ...loadModule("src/api/locations", dependencies),
      managedLocationNameSchema: { safeParse: value => value.trim() ? { success: true, data: value.trim() } : { success: false, error: { issues: [{ message: "Podaj nazwę." }] } } },
      googleReviewUrlSchema: { safeParse: () => ({ success: true }) },
      sortLocations: locations => locations,
      async createLocation(locationName, googleReviewUrl) { requests.push({ locationName, googleReviewUrl }); throw new ApiError(409); },
    };
    if (name === "@/src/api/locations" && kind === "location-management") return {
      ...loadModule("src/api/locations", dependencies),
      managedLocationNameSchema: { parse: value => value.trim() },
      googleReviewUrlSchema: { parse: value => value.trim() },
      sortLocations: locations => locations,
      async renameLocation(id, locationName) { requests.push({ rename: locationName }); return { ...collections.locations[0], name: locationName }; },
      async updateLocationReviewLink(id, url) { requests.push({ reviewUrl: url }); throw new ApiError(500); },
    };
    if (name === "@/src/api/usage-analytics") return { recordPanelAction() {} };
    if (name === "@/src/api/api-fetch") return { ApiError };
    if (name === "@/src/api/locations" || name === "@/src/messages") return loadModule(name.slice(2), dependencies);
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    // Phosphor's CommonJS build does not load under Node's require; icons are inert here.
    if (name === "@phosphor-icons/react") return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  };
  const source = ts.transpileModule(readFileSync(new URL(`../components/${kind}.tsx`, import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  new Function("require", "exports", source)(dependencies, exports);
  function elements(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(elements);
    if (typeof node.type === "function" && node.type.name === "LoadingIndicator") return elements(node.type(node.props));
    return [node, ...elements(node.props?.children)];
  }
  function render() {
    cursor = 0;
    if (kind === "account-management") return exports.AccountManagement({ account: { accountId: "staff", tenantRole: "ADMIN" } });
    if (kind === "location-creation-modal") return exports.LocationCreationModal({ accountId: "staff", isOpen: true, isRequired: true, onCreated() {}, onOpenChange() {} });
    if (kind === "location-management") return exports.LocationManagement({ accountId: "staff", onEditMenu() {}, onViewOrders() {} });
    return exports.OrderManagement({ accountId: "staff", canViewTenantOrders: true, canManageLocations: false, onSelectLocation() {} });
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
    popup: () => find(node => node.type === "PanelPopup" && elements(node).some(child => ["Nowe konto", "Zaproszenie wysłane do kolejki", "Nowe zamówienie"].includes(child.props?.children))),
    submit: () => find(node => node.type === "form").onSubmit({ preventDefault() {} }),
    filter: () => find(node => node.props?.label === "Lokal kolejki"),
    secret: () => find(node => node.type === "OneTimeSecret"),
    complete: result => complete(result),
  };
}

test("dismiss and reopen preserves pending creation and shows queued email without exposing a link", async () => {
  const view = workspace();
  view.open();
  view.email().onChange("invitee@example.com");
  const submission = view.submit();
  view.popup().onOpenChange(false);
  view.open();
  void view.submit();
  assert.equal(view.requests.length, 1);
  assert.equal(view.requests[0].email, "invitee@example.com");
  assert.equal(view.requests[0].replaceExisting, false);
  view.popup().onOpenChange(false);
  view.complete({ id: "invitation", email: "invitee@example.com", role: "OPERATOR", locationName: "Location" });
  await submission;
  assert.equal(view.popup().isOpen, true);
  assert.equal(view.nodes().some(node => node.type === "OneTimeSecret"), false);
  assert.ok(view.nodes().some(node => node.props?.children === "Zaproszenie wysłane do kolejki"));
  view.popup().onOpenChange(false);
  view.open();
  view.nodes().find(node => node.props?.children === "Gotowe").props.onPress();
  assert.equal(view.popup().isOpen, false);
});


test("dismiss and reopen cannot duplicate order creation by switching the queue filter", async () => {
  // A location without a menu opens directly at the label step.
  const view = workspace("order-management", { "location-menu": null });
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
  const status = loading.nodes().find(node => node.props?.role === "status");
  assert.ok(status);
  assert.ok(status.props.children.some(node => node.props?.children === "Wczytywanie zaproszeń"));
  const failed = workspace("account-management", { accounts: [account], errors: { invitations: new Error("Unavailable") } });
  assert.equal(failed.nodes().filter(node => node.type === "PanelCard").length, 1);
  const retry = failed.nodes().find(node => node.props?.["aria-label"] === "Spróbuj ponownie wczytać zaproszenia");
  assert.ok(retry);
  retry.props.onPress();
  assert.deepEqual(failed.requests, [{ retry: "invitations" }]);
});


test("dismissed location drafts cannot carry into a newly created location", () => {
  const locations = [{ id: "a", name: "Location A", status: "ENABLED", googleReviewUrl: "https://example.com/a" }];
  const view = workspace("location-management", { locations });
  const find = predicate => { const node = view.nodes().find(predicate); assert.ok(node); return node.props; };
  find(node => node.type === "PanelCard").onPress();
  find(node => node.props?.name === "location-name").onChange("Draft A");
  find(node => node.props?.name === "google-review-url").onChange("https://example.com/draft-a");
  find(node => node.type === "PanelPopup" && node.props?.["aria-label"]).onOpenChange(false);
  const created = { id: "b", name: "Location B", status: "ENABLED", googleReviewUrl: "https://example.com/b" };
  locations.push(created);
  find(node => node.type === "LocationCreationModal").onCreated(created);
  assert.equal(find(node => node.type === "PanelDetailHeader").title, "Location B");
  assert.equal(find(node => node.props?.name === "location-name").value, "Location B");
  assert.equal(find(node => node.props?.name === "google-review-url").value, "https://example.com/b");
});


test("location creation keeps step input and creates only after the last step", async () => {
  const view = workspace("location-creation-modal");
  const field = name => view.nodes().find(node => node.props?.name === name)?.props;
  // Proceed and Create share a position, so a submit right after a step change is ignored.
  const settle = () => new Promise(resolve => setTimeout(resolve, 401));
  await view.submit();
  assert.ok(field("location-name").errorMessage);
  field("location-name").onChange("Bistro");
  await view.submit();
  assert.equal(field("location-name"), undefined);
  field("google-review-url").onChange("https://g.page/r/bistro/review");
  view.nodes().find(node => [node.props?.children].flat().includes("Wstecz")).props.onPress();
  assert.equal(field("location-name").value, "Bistro");
  await view.submit();
  assert.equal(field("google-review-url").value, "https://g.page/r/bistro/review");
  assert.equal(view.requests.length, 0);
  await settle();
  await view.submit();
  assert.deepEqual(view.requests, [{ locationName: "Bistro", googleReviewUrl: "https://g.page/r/bistro/review" }]);
  assert.ok(field("location-name").errorMessage, "A duplicate name returns to the name step");
});


test("invitation creation rejects invalid email with a field error before requesting email delivery", async () => {
  const view = workspace();
  view.open();
  view.email().onChange("invalid");
  await view.submit();
  assert.equal(view.requests.length, 0);
  assert.ok(view.email().errorMessage);
  view.email().onChange("invitee@example.com");
  assert.equal(view.email().errorMessage, undefined);
});

const invitation = { id: "invite", email: "invitee@example.com", role: "OPERATOR", locationName: "Location", issuedByEmail: "owner@example.com", createdAt: "2026-01-01T00:00:00Z", expiresAt: "2099-01-01T00:00:00Z" };
test("duplicate invitation requires explicit replacement confirmation", async () => {
  const view = workspace("account-management", { conflict: true });
  view.open();
  view.email().onChange("invitee@example.com");
  await view.submit();
  assert.equal(view.requests.length, 1);
  assert.equal(view.email().isDisabled, true);
  const confirm = view.nodes().find(node => node.type === "HoldToConfirmButton");
  assert.ok(confirm);
  const submission = confirm.props.onConfirm();
  assert.equal(view.requests[1].replaceExisting, true);
  view.complete({ ...invitation, state: "PENDING", deliveryState: "QUEUED" });
  await submission;
});
test("pending failed delivery can be resent only through confirmation", async () => {
  const view = workspace("account-management", { invitations: [{ ...invitation, deliveryState: "FAILED" }] });
  view.nodes().find(node => node.type === "PanelCard").props.onPress();
  assert.equal(view.requests.length, 0);
  const confirm = view.nodes().find(node => node.type === "HoldToConfirmButton" && String(node.props.children).includes("wysłać ponownie"));
  assert.ok(confirm);
  await confirm.props.onConfirm();
  assert.deepEqual(view.requests[0], { resend: "invite" });
});
test("location Save sends only changed fields and keeps the draft when one fails", async () => {
  const locations = [{ id: "a", name: "Location A", status: "ENABLED", googleReviewUrl: "https://example.com/a" }];
  const view = workspace("location-management", { locations });
  const find = predicate => { const node = view.nodes().find(predicate); assert.ok(node); return node.props; };
  find(node => node.type === "PanelCard").onPress();
  find(node => node.props?.name === "google-review-url").onChange("https://example.com/draft");
  await find(node => node.type === "form").onSubmit({ preventDefault() {} });
  assert.deepEqual(view.requests, [{ reviewUrl: "https://example.com/draft" }]);
  assert.equal(find(node => node.props?.name === "google-review-url").value, "https://example.com/draft");
});
test("redeemed invitations are represented by their account, not listed", () => {
  const view = workspace("account-management", { invitations: [{ ...invitation, state: "REDEEMED" }] });
  assert.equal(view.nodes().filter(node => node.type === "PanelCard").length, 0);
});
for (const state of ["REVOKED", "REPLACED", "EXPIRED"]) {
  test(`${state} invitation remains in history without mutation controls`, () => {
    const view = workspace("account-management", { invitations: [{ ...invitation, state }] });
    view.nodes().find(node => node.type === "PanelCard").props.onPress();
    assert.equal(view.nodes().filter(node => node.type === "HoldToConfirmButton").length, 0);
  });
}
test("full invitation history renders without sorting or paging controls", () => {
  const invitations = Array.from({ length: 25 }, (_, index) => ({
    id: `invite-${index}`, email: `invitee-${index}@example.com`, role: "OPERATOR",
    locationName: "Location", issuedByEmail: "owner@example.com",
    createdAt: "2026-01-01T00:00:00Z", expiresAt: "2099-01-01T00:00:00Z",
  }));
  const view = workspace("account-management", { invitations });
  const nodes = view.nodes();
  assert.equal(nodes.filter(node => node.type === "PanelCard").length, 25);
  assert.equal(nodes.some(node => node.props?.label === "Kolejność zaproszeń"), false);
  assert.equal(nodes.some(node => typeof node.props?.children === "string" && /strona zaproszeń/.test(node.props.children)), false);
});

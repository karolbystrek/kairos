import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

// Render the panel's event flow without a browser; UI and request boundaries are stubs.
function panel(screen = "dashboard", loginResult, initialHash = "") {
  const listeners = new Set();
  let currentHash = initialHash;
  const browser = {
    location: {
      get hash() { return currentHash; },
      set hash(value) { currentHash = value.startsWith("#") ? value : "#" + value; },
    },
    addEventListener: (_, callback) => listeners.add(callback),
    removeEventListener: (_, callback) => listeners.delete(callback),
  };
  let locations = [{ id: "location" }];
  const state = [];
  let cursor = 0;
  let pending = false;
  let failure;
  let offline = false;
  let expireSession;
  const requests = [];
  const actions = [];
  const cacheClears = [];
  const redirects = [];
  const ui = Object.fromEntries(["Alert", "AlertDialog", "Button", "Dropdown", "Link", "Spinner", "Tabs", "Tooltip"].map(name => [name, new Proxy(function () {}, {
    get: (_, part) => `${name}.${String(part)}`,
  })]));
  let account = { accountId: "staff", capabilities: [] };
  let accountError;
  let verification;
  let loading = false;
  class ApiError extends Error {
    constructor(status) { super(); this.status = status; }
  }
  const dependencies = (name) => {
    if (name === "next/navigation") return { useRouter: () => ({ push: path => redirects.push(path), replace: path => redirects.push(path) }) };
    if (name === "react") return {
      useState(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = initial;
        return [state[index], value => { state[index] = value; }];
      },
      useEffect(effect) { effect(); },
      useSyncExternalStore(subscribe, snapshot) { subscribe(() => {}); return snapshot(); },
    };
    if (name === "@heroui/react") return ui;
    if (name === "swr") return {
      default: key => key?.[0] === "locations" ? { data: locations, mutate: async () => {} } : key?.[1] === "verification" || key === null ? { data: verification, mutate: async value => { verification = value; } } : ({ data: account, error: accountError, isLoading: loading, mutate: async value => {
        cacheClears.push("account");
        account = value;
        accountError = undefined;
      } }),
      useSWRConfig: () => ({ mutate: async () => cacheClears.push("staff") }),
    };
    if (name === "swr/mutation") return { default: (key, mutation) => ({
      isMutating: key[1] === "logout" && pending,
      error: key[1] === "logout" ? failure : undefined,
      reset() { failure = undefined; },
      async trigger(arg) { return mutation(key, { arg }); },
    }) };
    if (name === "@/src/api/authentication") return {
      pendingVerificationKey: ["authentication", "verification"],
      async login() { return loginResult ?? { accountId: "staff", capabilities: [] }; },
      async logout() {
        requests.push("logout");
        if (offline) failure = new Error("Offline");
        return !offline;
      },
    };
    if (name === "@/src/api/usage-analytics") return { recordPanelAction: event => actions.push(event) };
    if (name === "@/src/api/api-fetch") return { ApiError };
    if (name === "@/src/api/auth-state") return { subscribeToAuthenticationRequired(callback) { expireSession = callback; return () => {}; } };
    if (name === "@/src/api/cache-keys") return { currentAccountKey: ["authentication", "current-account"], staffLocationsKey: () => ["locations"], isStaffCacheKey: () => true };
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    // Phosphor's CommonJS build does not load under Node's require; icons are inert here.
    if (name === "@phosphor-icons/react") return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  };
  const exports = {};
  const source = ts.transpileModule(
    readFileSync(new URL("../components/staff-panel.tsx", import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } },
  ).outputText;
  new Function("require", "exports", "window", source)(dependencies, exports, browser);
  function render() { cursor = 0; return exports.StaffPanel({ screen }); }
  function elements(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(elements);
    return [node, ...elements(node.props?.children)];
  }
  function find(predicate) {
    const result = elements(render()).find(predicate);
    assert.ok(result, "Expected account action control to be available");
    return result.props;
  }
  return {
    requests, actions, cacheClears, redirects, render, ApiError,
    hash: () => browser.location.hash,
    navigate(hash) { browser.location.hash = hash; listeners.forEach(callback => callback()); },
    locations(value) { locations = value; },
    workspace: key => find(node => node.type === ui.Tabs).onSelectionChange(key),
    tabs: () => elements(render()).filter(node => node.type === "Tabs.Tab").map(node => node.props.id),
    selected: () => find(node => node.type === ui.Tabs).selectedKey,
    session(value, error, isLoading = false) { account = value; accountError = error; loading = isLoading; },
    verification(value) { verification = value; },
    signIn: () => find(node => node.type?.name === "SignedOutPanel").onSignIn({ email: "staff@example.com", password: "password" }),
    expire: () => expireSession(),
    async choose(key) {
      find(node => node.props?.["aria-label"] === "Opcje konta").onAction(key);
      await new Promise(resolve => setImmediate(resolve));
    },
    changePassword: () => find(node => node.type === "PasswordChangeForm").onChanged(),
    logoutError: () => elements(render()).find(node => node.type?.name === "DismissibleNotice")?.props.title,
    pending: value => { pending = value; },
    fail: () => { offline = true; },
  };
}

test("signed-in login visitors go to the dashboard", () => {
  const view = panel("login");
  view.render();
  assert.deepEqual(view.redirects, ["/dashboard"]);
});

test("signed-out dashboard visitors go to login only after the session check", () => {
  const view = panel();
  view.session(undefined, undefined, true);
  view.render();
  assert.deepEqual(view.redirects, []);
  view.session(undefined);
  view.render();
  assert.deepEqual(view.redirects, ["/login"]);
});

test("dashboard session failures retain recovery instead of redirecting", () => {
  const view = panel();
  view.session(undefined, new Error("Offline"));
  view.render();
  assert.deepEqual(view.redirects, []);
});

test("an expired cached account on login does not redirect back to the dashboard", () => {
  const view = panel("login");
  view.session({ accountId: "staff", capabilities: [] }, new view.ApiError(401));
  view.render();
  assert.deepEqual(view.redirects, []);
});

test("successful login navigates to the dashboard after updating the session", async () => {
  const view = panel("login");
  view.session(undefined);
  await view.signIn();
  view.render();
  assert.deepEqual(view.redirects, ["/dashboard"]);
});

test("sign-out happens without confirmation and clears the session", async () => {
  const view = panel();
  await view.choose("logout");
  assert.deepEqual(view.requests, ["logout"]);
  view.render();
  assert.deepEqual(view.redirects, ["/login"]);
  assert.deepEqual(view.cacheClears, ["account", "staff"]);
});

test("failed sign-out keeps the session, shows the error and prevents resubmission while pending", async () => {
  const view = panel();
  view.fail();
  await view.choose("logout");
  assert.deepEqual(view.cacheClears, []);
  assert.equal(view.logoutError(), "Nie udało się wylogować");
  view.pending(true);
  await view.choose("logout");
  assert.deepEqual(view.requests, ["logout"]);
});

test("password change opens its page and ends on login with a confirmation", async () => {
  const view = panel();
  await view.choose("password");
  assert.deepEqual(view.redirects, ["/dashboard/password"]);

  const page = panel("password");
  await page.changePassword();
  page.render();
  assert.deepEqual(page.redirects, ["/login?password-changed"]);
  assert.deepEqual(page.cacheClears, ["account", "staff"]);

  const signedOut = panel("password");
  signedOut.session(undefined);
  signedOut.render();
  assert.deepEqual(signedOut.redirects, ["/login"]);
});

test("workspace entries count once without counting rerenders or the selected tab", () => {
  const view = panel();
  view.session({ accountId: "staff", capabilities: ["MANAGE_LOCATIONS", "MANAGE_MENUS", "MANAGE_EXTERNAL_INTEGRATIONS", "CHOOSE_LOCATION_MENU"] });
  view.workspace("menus");
  view.render();
  view.workspace("menus");
  view.workspace("integrations");
  view.workspace("orders");
  view.workspace("menus");
  assert.deepEqual(view.actions, ["menu_editor_opened", "integration_settings_opened", "menu_editor_opened"]);
});

test("analytics tabs follow capabilities and revoked platform access leaves its tab", () => {
  const view = panel();
  view.session({ accountId: "staff", capabilities: ["MANAGE_ORDERS"] });
  assert.deepEqual(view.tabs(), []);
  view.session({ accountId: "staff", capabilities: ["MANAGE_ORDERS", "VIEW_ANALYTICS", "VIEW_PLATFORM_ANALYTICS"] });
  assert.deepEqual(view.tabs(), ["orders", "analytics", "platform"]);
  view.workspace("platform");
  assert.equal(view.selected(), "platform");
  view.session({ accountId: "staff", capabilities: ["MANAGE_ORDERS", "VIEW_ANALYTICS"] });
  assert.deepEqual(view.tabs(), ["orders", "analytics"]);
  assert.equal(view.selected(), "orders");
});

test("unverified login stays outside the staff session and displays verification", async () => {
  const view = panel("login", { verificationRequired: true, email: "staff@example.com", resendAvailableAt: "2026-10-12T12:00:00Z" });
  view.session(undefined);
  await view.signIn();
  assert.equal(view.render().type, "EmailVerification");
  assert.deepEqual(view.redirects, []);
  assert.deepEqual(view.cacheClears, []);
});
test("login restores a pending verification after reload", () => {
  const view = panel("login");
  view.session(undefined, new view.ApiError(401));
  view.verification({ verificationRequired: true, email: "staff@example.com", resendAvailableAt: "2026-10-12T12:00:00Z" });
  assert.equal(view.render().type, "EmailVerification");
  assert.deepEqual(view.redirects, []);
});


test("direct links and reload preserve the workspace while locations load", () => {
  const account = { accountId: "staff", capabilities: ["MANAGE_LOCATIONS", "MANAGE_EXTERNAL_INTEGRATIONS"] };
  const view = panel("dashboard", undefined, "#integrations");
  view.session(account);
  view.locations(undefined);
  assert.equal(view.selected(), "integrations");
  assert.ok(view.tabs().includes("integrations"));
  view.workspace("locations");
  assert.equal(view.hash(), "#locations");
  const reloaded = panel("dashboard", undefined, view.hash());
  reloaded.session(account);
  assert.equal(reloaded.selected(), "locations");
  view.navigate("#integrations");
  assert.equal(view.selected(), "integrations");
  view.navigate("#locations");
  assert.equal(view.selected(), "locations");
});

test("unknown, inaccessible and onboarding-disabled fragments fall back to orders", () => {
  const view = panel("dashboard", undefined, "#unknown");
  view.session({ accountId: "staff", capabilities: ["MANAGE_LOCATIONS"] });
  assert.equal(view.selected(), "orders");
  for (const hash of ["#menus", "#accounts", "#integrations", "#analytics", "#platform"]) {
    view.navigate(hash);
    assert.equal(view.selected(), "orders");
  }
  view.session({ accountId: "staff", capabilities: ["MANAGE_LOCATIONS", "MANAGE_EXTERNAL_INTEGRATIONS"] });
  view.navigate("#integrations");
  view.locations([]);
  assert.equal(view.selected(), "orders");
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

// Render the panel's event flow without a browser; UI and request boundaries are stubs.
function panel(screen = "dashboard") {
  const state = [];
  let cursor = 0;
  let pending = false;
  let failure;
  let expireSession;
  const requests = [];
  const cacheClears = [];
  const redirects = [];
  const ui = Object.fromEntries(["Alert", "AlertDialog", "Button", "Dropdown", "Link", "Spinner", "Tabs", "Tooltip"].map(name => [name, new Proxy(function () {}, {
    get: (_, part) => `${name}.${String(part)}`,
  })]));
  let account = { accountId: "staff", capabilities: [] };
  let accountError;
  let loading = false;
  class ApiError extends Error {
    constructor(status) { super(); this.status = status; }
  }
  const dependencies = (name) => {
    if (name === "next/navigation") return { useRouter: () => ({ replace: path => redirects.push(path) }) };
    if (name === "react") return {
      useState(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = initial;
        return [state[index], value => { state[index] = value; }];
      },
      useEffect(effect) { effect(); },
    };
    if (name === "@heroui/react") return ui;
    if (name === "swr") return {
      default: () => ({ data: account, error: accountError, isLoading: loading, mutate: async value => {
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
      async login() { return { accountId: "staff", capabilities: [] }; },
      async logout(everywhere) { requests.push(everywhere); return !failure; },
    };
    if (name === "@/src/api/api-fetch") return { ApiError };
    if (name === "@/src/api/auth-state") return { subscribeToAuthenticationRequired(callback) { expireSession = callback; return () => {}; } };
    if (name === "@/src/api/cache-keys") return { currentAccountKey: ["authentication", "current-account"], staffLocationsKey: () => [], isStaffCacheKey: () => true };
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  };
  const exports = {};
  const source = ts.transpileModule(
    readFileSync(new URL("../components/staff-panel.tsx", import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } },
  ).outputText;
  new Function("require", "exports", source)(dependencies, exports);
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
    requests, cacheClears, redirects, render, ApiError,
    session(value, error, isLoading = false) { account = value; accountError = error; loading = isLoading; },
    signIn: () => find(node => node.type?.name === "SignedOutPanel").onSignIn({ email: "staff@example.com", password: "password" }),
    expire: () => expireSession(),
    passwordDialog: () => find(node => node.type === "PasswordChangeDialog"),
    choose: key => find(node => node.props?.["aria-label"] === "Account actions").onAction(key),
    dialog: () => find(node => node.props?.isOpen !== undefined && node.props?.onOpenChange && node.props?.role === "alertdialog"),
    async confirm() {
      find(node => node.props?.variant === "danger" && node.props?.onPress).onPress();
      await new Promise(resolve => setImmediate(resolve));
    },
    pending: value => { pending = value; },
    fail: () => { failure = new Error("Offline"); },
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

for (const scope of ["device", "everywhere"]) {
  test(`${scope} sign-out waits for confirmation and cancellation sends no request`, async () => {
    const view = panel();
    view.choose(scope);
    assert.equal(view.dialog().isOpen, true);
    assert.deepEqual(view.requests, []);
    view.dialog().onOpenChange(false);
    assert.equal(view.dialog().isOpen, false);
    assert.deepEqual(view.requests, []);
    view.choose(scope);
    await view.confirm();
    assert.deepEqual(view.requests, [scope === "everywhere"]);
    view.render();
    assert.deepEqual(view.redirects, ["/login"]);
    assert.deepEqual(view.cacheClears, ["account", "staff"]);
  });
}

test("global sign-out retains failure for retry, allows dismissal while pending, and prevents resubmission", async () => {
  const view = panel();
  view.choose("everywhere");
  view.fail();
  await view.confirm();
  assert.equal(view.dialog().isOpen, true);
  assert.deepEqual(view.cacheClears, []);
  view.pending(true);
  view.dialog().onOpenChange(false);
  await view.confirm();
  assert.equal(view.dialog().isOpen, false);
  assert.deepEqual(view.requests, [true]);
});


test("expired authentication closes account dialogs before a later sign-in", () => {
  const view = panel();
  view.choose("password");
  assert.equal(view.passwordDialog().isOpen, true);
  view.expire();
  view.render();
  assert.deepEqual(view.redirects, ["/login"]);
  view.session({ accountId: "staff", capabilities: [] });
  assert.equal(view.passwordDialog().isOpen, false);
  view.choose("everywhere");
  view.expire();
  view.session({ accountId: "staff", capabilities: [] });
  assert.equal(view.dialog().isOpen, false);
});

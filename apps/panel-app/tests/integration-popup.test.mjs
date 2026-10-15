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

function workspace({ delayMutation = false } = {}) {
  let finishMutation;
  const hooks = new Map();
  const modules = new Map();
  const requests = [];
  const completions = [];
  let scope;
  let cursor;
  const subscription = { id: "webhook", name: "Webhook", status: "DISABLED", locationIds: [], eventTypes: [], signingSecretVersions: [] };
  const dependencies = name => {
    if (name === "react") return {
      useId: () => "id",
      useState(initial) {
        const state = hooks.get(scope);
        const index = cursor++;
        if (!(index in state)) state[index] = initial;
        return [state[index], value => { state[index] = typeof value === "function" ? value(state[index]) : value; }];
      },
      useRef(initial) {
        const state = hooks.get(scope);
        return state[cursor++] ??= { current: initial };
      },
    };
    if (name === "@heroui/react") return new Proxy({}, { get: (_, key) => new Proxy(() => {}, { get: (_, part) => `${String(key)}.${String(part)}` }) });
    if (name === "swr") return { default: key => ({
      data: key === "integrations" ? [{ id: "integration", name: "Integration", status: "ENABLED" }] : key === "locations" ? [{ id: "location", name: "Location", status: "ENABLED" }] : key === "webhooks" ? [subscription] : [],
      mutate: () => delayMutation ? new Promise(resolve => { finishMutation = resolve; }) : Promise.resolve(),
    }) };
    if (name === "@/src/api/cache-keys") return {
      staffIntegrationsKey: () => "integrations", staffLocationsKey: () => "locations",
      staffApiKeysKey: () => "keys", staffWebhookSubscriptionsKey: () => "webhooks",
    };
    if (name === "@/src/api/api-keys" || name === "@/src/api/webhook-subscriptions") return new Proxy({}, { get: (_, key) => () => {
      if (!["issueApiKey", "rotateWebhookSigningSecret", "createWebhookSubscription"].includes(key)) return Promise.resolve([]);
      requests.push(key);
      return new Promise(resolve => { completions.push(resolve); });
    } });
    if (["@/components/integrations/api-key-management", "@/components/integrations/webhook-subscription-management", "@/components/status-chip"].includes(name)) return load(name.replace("@/", ""));
    if (name === "@/src/api/locations" || name === "@/src/messages") return loadModule(name.slice(2), dependencies);
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    // Phosphor's CommonJS build does not load under Node's require; icons are inert here.
    if (name === "@phosphor-icons/react") return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  };
  function load(path) {
    if (modules.has(path)) return modules.get(path);
    const exports = {};
    const source = ts.transpileModule(readFileSync(new URL(`../${path}.tsx`, import.meta.url), "utf8"), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    }).outputText;
    new Function("require", "exports", source)(dependencies, exports);
    modules.set(path, exports);
    return exports;
  }
  const { IntegrationManagement } = load("components/integration-management");
  function renderComponent(component, props) {
    scope = component.name;
    cursor = 0;
    if (!hooks.has(scope)) hooks.set(scope, []);
    return component(props);
  }
  function elements(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(elements);
    if (typeof node.type === "function" && ["IntegrationDetails", "ApiKeyManagement", "WebhookSubscriptionManagement"].includes(node.type.name)) return elements(renderComponent(node.type, node.props));
    return [node, ...elements(node.props?.children)];
  }
  function nodes() { return elements(renderComponent(IntegrationManagement, { accountId: "staff" })); }
  function find(predicate) { const node = nodes().find(predicate); assert.ok(node); return node.props; }
  return {
    requests,
    finishMutation: () => finishMutation(),
    open: () => find(node => node.type === "PanelCard").onPress(),
    dismiss() {
      find(node => node.type === "PanelPopup" && node.props["aria-label"]).onOpenChange(false);
      for (const key of hooks.keys()) if (key !== "IntegrationManagement") hooks.delete(key);
    },
    issue: () => find(node => node.type === "form" && node.props.children?.some?.(child => child?.props?.name === "api-key-name")).onSubmit({ preventDefault() {} }),
    rotate: () => find(node => node.props?.onPress && (JSON.stringify(node.props.children) ?? "").includes("Zmień sekret")).onPress(),
    secret: () => find(node => node.type === "OneTimeSecret"),
    complete: (result, index = completions.length - 1) => completions[index](result),
    resetAccount() { hooks.clear(); },
    nodes,
  };
}

for (const operation of ["issue", "rotate"]) {
  test(`${operation}: dismissal and reopening preserve ownership and the secret until confirmation`, async () => {
    const view = workspace();
    view.open();
    const submission = view[operation]();
    view.dismiss();
    view.open();
    await view[operation]();
    // The other secret-producing operation shares ownership, even across tabs.
    await view[operation === "issue" ? "rotate" : "issue"]();
    assert.equal(view.requests.length, 1);
    view.dismiss();
    view.complete(operation === "issue" ? { apiKey: { id: "key", name: "Key" }, secret: "once" } : { version: { id: "version" }, signingSecret: "once" });
    await submission;
    assert.equal(view.secret().secret.value, "once");
    assert.equal(view.secret().secret.value, "once");
    view.secret().onConfirmed();
    view.open();
    const next = view[operation]();
    assert.equal(view.requests.length, 2);
    view.complete(operation === "issue" ? { apiKey: { id: "second", name: "Second" }, secret: "next" } : { version: { id: "second" }, signingSecret: "next" });
    await next;
    assert.equal(view.secret().secret.value, "next");
    view.resetAccount();
    assert.ok(!view.nodes().some(node => node.type === "OneTimeSecret"));
  });
}


test("a late secret from the previous account cannot enter the current account", async () => {
  const view = workspace();
  view.open();
  const oldSubmission = view.issue();
  view.resetAccount();
  view.open();
  const newSubmission = view.issue();
  view.complete({ apiKey: { id: "old", name: "Old" }, secret: "old-secret" }, 0);
  await oldSubmission;
  assert.ok(!view.nodes().some(node => node.type === "OneTimeSecret"));
  view.complete({ apiKey: { id: "new", name: "New" }, secret: "new-secret" });
  await newSubmission;
  assert.equal(view.secret().secret.value, "new-secret");
});


test("confirmation cannot release ownership before the original cache update finishes", async () => {
  const view = workspace({ delayMutation: true });
  view.open();
  const submission = view.issue();
  view.complete({ apiKey: { id: "key", name: "Key" }, secret: "once" });
  await Promise.resolve();
  view.secret().onConfirmed();
  view.open();
  await view.issue();
  assert.equal(view.requests.length, 1);
  view.finishMutation();
  await submission;
  const next = view.issue();
  assert.equal(view.requests.length, 2);
  view.complete({ apiKey: { id: "next", name: "Next" }, secret: "next" });
  await Promise.resolve();
  view.finishMutation();
  await next;
  assert.equal(view.secret().secret.value, "next");
});

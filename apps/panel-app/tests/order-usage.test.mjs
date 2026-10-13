import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
function queue() {
  const state = [];
  let cursor = 0;
  let loading = true;
  const actions = [];
  const ui = Object.fromEntries(["Alert", "Button", "ListBox", "Spinner", "Tooltip"].map(name => [name, new Proxy(function () {}, { get: (_, part) => `${name}.${String(part)}` })]));
  const dependencies = name => {
    if (name === "react") return {
      useState(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = typeof initial === "function" ? initial() : initial;
        return [state[index], value => { state[index] = value; }];
      },
      useRef(initial) { const index = cursor++; return state[index] ??= { current: initial }; },
      useMemo(fn) { return fn(); },
      useEffect(fn) { fn(); },
    };
    if (name === "@heroui/react") return ui;
    if (name === "swr") return { default: key => ({
      data: key?.[2] === "locations" ? [{ id: "location-one", name: "Cafe", status: "ENABLED" }] : [],
      isLoading: loading,
    }) };
    if (name === "swr/mutation") return { default: () => ({ reset() {}, isMutating: false }) };
    if (name === "@/src/api/cache-keys") return { staffLocationsKey: () => ["staff", "account", "locations"], staffLocationMenuKey: () => null };
    if (name === "@/src/api/usage-analytics") return { recordPanelAction: (...args) => actions.push(args) };
    if (name === "@/src/api/orders") return { subscribeToOrderQueue: () => () => {} };
    if (name.startsWith("@/")) return new Proxy({}, { get: (_, key) => String(key) });
    // Phosphor's CommonJS build does not load under Node's require; icons are inert here.
    if (name === "@phosphor-icons/react") return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  };
  const exports = {};
  const source = ts.transpileModule(readFileSync(new URL("../components/order-management.tsx", import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  new Function("require", "exports", "window", source)(dependencies, exports, { setInterval() {}, clearInterval() {} });
  function render(isActive = true) {
    cursor = 0;
    return exports.OrderManagement({ accountId: "staff", canManageLocations: true, canViewTenantOrders: true, isActive });
  }
  function elements(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(elements);
    return [node, ...elements(node.props?.children)];
  }
  return {
    actions, render,
    loaded() { loading = false; },
    openForm() { elements(render()).find(node => node.props?.["aria-label"] === "Nowe zamówienie").props.onPress(); },
    closeForm() { elements(render()).find(node => node.props?.isOpen !== undefined && node.props?.onOpenChange).props.onOpenChange(false); },
  };
}

test("initial visible queue counts once after location loading and again on returning", () => {
  const view = queue();
  view.render();
  assert.deepEqual(view.actions, []);
  view.loaded();
  view.render();
  view.render();
  view.render(false);
  view.render(false);
  view.render();
  assert.deepEqual(view.actions, [["queue_opened", undefined], ["queue_opened", undefined]]);
});

test("each real form opening counts its default location even from the tenant queue", () => {
  const view = queue();
  view.loaded();
  view.openForm();
  view.openForm();
  view.closeForm();
  view.openForm();
  assert.deepEqual(view.actions, [["queue_opened", undefined], ["order_creation_form_opened", "location-one"], ["order_creation_form_opened", "location-one"]]);
});

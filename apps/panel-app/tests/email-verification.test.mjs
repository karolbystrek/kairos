import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
function view(code, cooldown = 60) {
  const state = [code, false, "", Date.now()];
  let cursor = 0;
  const requests = [];
  const exports = {};
  const source = ts.transpileModule(readFileSync(new URL("../components/email-verification.tsx", import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  new Function("require", "exports", source)(name => {
    if (name === "react") return {
      useState() { const index = cursor++; return [state[index], value => { state[index] = value; }]; },
      useEffect() {},
    };
    if (name === "@/src/api/authentication") return {
      async verifyEmail(value) { requests.push(["verify", value]); return { accountId: "owner" }; },
      async resendVerification() { requests.push(["resend"]); return {}; },
      async logout() { requests.push(["logout"]); },
    };
    if (name === "@/src/api/api-fetch") return { ApiError: class extends Error {} };
    if (name === "@heroui/react" || name.startsWith("./")) return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  }, exports);
  const props = {
    verification: { email: "owner@example.com", resendAvailableAt: new Date(state[3] + cooldown * 1000).toISOString() },
    async onVerified(account) { requests.push(["verified", account.accountId]); },
    async onChanged() { requests.push(["changed"]); },
    async onCancelled() { requests.push(["cancelled"]); },
  };
  function render() { cursor = 0; return exports.EmailVerification(props); }
  function elements(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(elements);
    return [node, ...elements(node.props?.children)];
  }
  return {
    requests,
    elements: () => elements(render()),
    async submit() {
      elements(render()).find(node => node.type === "form").props.onSubmit({ preventDefault() {} });
      await new Promise(resolve => setImmediate(resolve));
    },
  };
}
test("verification rejects incomplete codes before sending a request", async () => {
  const form = view("123");
  await form.submit();
  assert.deepEqual(form.requests, []);
  assert.ok(form.elements().some(node => node.props?.role === "status"));
});
test("six-digit verification completes authentication", async () => {
  const form = view("123456");
  await form.submit();
  assert.deepEqual(form.requests, [["verify", "123456"], ["verified", "owner"]]);
});
test("resend respects the server cooldown and change-email clears the server session", async () => {
  const form = view("", 60);
  const buttons = form.elements().filter(node => node.type === "Button");
  assert.equal(buttons[1].props.isDisabled, true);
  assert.equal(buttons[1].props.children, "Wyślij ponownie za 60 s");
  buttons[2].props.onPress();
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(form.requests, [["logout"], ["cancelled"]]);
});

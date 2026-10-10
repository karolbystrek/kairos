import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

async function submitRegistration({ invited = false, fails = false } = {}) {
  const redirects = [];
  const requests = [];
  const values = ["owner@example.com", "long-password", "long-password"];
  let cursor = 0;
  const exports = {};
  const source = ts.transpileModule(
    readFileSync(new URL("../components/registration-form.tsx", import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } },
  ).outputText;
  async function createAccount(kind, input) {
    requests.push({ kind, input });
    if (fails) throw new Error("Offline");
  }
  new Function("require", "exports", "window", source)(name => {
    if (name === "react") return {
      useSyncExternalStore: () => invited ? "invitation-token" : "",
      useState: initial => [cursor < values.length ? values[cursor++] : initial, () => {}],
    };
    if (name === "swr") return { default: key => ({ data: key?.[0] === "invitation-preview" ? { email: "invited@example.com", locationName: "Restaurant", role: "OPERATOR" } : undefined }) };
    if (name === "@/src/api/tenant-registrations") return {
      registrationInputSchema: { parse: input => input },
      registerTenant: input => createAccount("tenant", input),
    };
    if (name === "@/src/api/account-invitations") return {
      redeemAccountInvitation: input => createAccount("invitation", input),
    };
    if (name === "@/src/api/api-fetch") return { ApiError: class extends Error {} };
    if (name === "@heroui/react" || name.startsWith("@/") || name.startsWith("./")) return new Proxy({}, { get: (_, key) => String(key) });
    return require(name);
  }, exports, {
    location: { href: "https://panel.example.com/account-registration", assign: path => redirects.push(path) },
  });
  function form(node) {
    if (!node || typeof node !== "object") return;
    if (node.type === "form") return node;
    for (const child of [node.props?.children].flat()) {
      const found = form(child);
      if (found) return found;
    }
  }
  const tree = exports.RegistrationForm({ invited });
  function fields(node) {
    if (!node || typeof node !== "object") return [];
    if (Array.isArray(node)) return node.flatMap(fields);
    return [...(node.type === "FormTextField" ? [node.props] : []), ...fields(node.props?.children)];
  }
  const rendered = form(tree);
  assert.ok(rendered);
  await rendered.props.onSubmit({ preventDefault() {} });
  return { redirects, requests, fields: fields(tree) };
}

for (const invited of [false, true]) {
  test(`${invited ? "invitation" : "public"} registration opens the dashboard after success`, async () => {
    const view = await submitRegistration({ invited });
    assert.equal(view.requests[0].kind, invited ? "invitation" : "tenant");
    assert.deepEqual(view.redirects, ["/dashboard"]);
  });
}

test("failed registration does not navigate away", async () => {
  const view = await submitRegistration({ fails: true });
  assert.deepEqual(view.redirects, []);
});

test("invited registration fixes the email from the invitation instead of editable form state", async () => {
  const view = await submitRegistration({ invited: true });
  const email = view.fields.find(field => field.name === "email");
  assert.equal(email.value, "invited@example.com");
  assert.equal(email.isReadOnly, true);
  assert.equal(view.requests[0].input.email, "invited@example.com");
});

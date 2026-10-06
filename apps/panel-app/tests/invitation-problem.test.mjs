import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

function registrationPage() {
  let url = new URL("https://panel.example.com/account-registration?source=team#invitation=secret&keep=yes");
  const state = { previous: "orders" };
  const events = [];
  const window = {
    get location() { return { href: url.href }; },
    history: {
      state,
      replaceState(nextState, _title, nextUrl) {
        assert.equal(nextState, state);
        url = new URL(nextUrl);
      },
    },
    dispatchEvent(event) { events.push(event.type); },
  };
  class ApiError extends Error {
    constructor(status, problem) { super(); this.status = status; this.problem = problem; }
  }
  const exports = {};
  const source = ts.transpileModule(
    readFileSync(new URL("../components/registration-form.tsx", import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } },
  ).outputText;
  new Function("require", "exports", "window", source)(
    (name) => name === "@/src/api/api-fetch" ? { ApiError }
      : name === "zod" || name === "react/jsx-runtime" ? require(name) : {},
    exports, window,
  );
  return { page: exports, ApiError, events, get url() { return url; } };
}

for (const [reason, status, guidance] of [
  ["expired", 410, /expired/i],
  ["revoked", 410, /revoked/i],
  ["redeemed", 410, /already.*used/i],
  ["invalid", 404, /invalid/i],
]) {
  test(`${reason} invitations remove their bearer fragment and give safe guidance`, () => {
    const view = registrationPage();
    const error = new view.ApiError(status, { type: `urn:kairos:problem:account-invitation-${reason}`, detail: "secret provider detail" });
    const message = view.page.handleInvitationProblem(error);
    assert.match(message ?? "", guidance);
    assert.doesNotMatch(message, /secret|provider/);
    assert.equal(view.url.href, "https://panel.example.com/account-registration?source=team#keep=yes");
    assert.deepEqual(view.events, ["hashchange"]);
  });
}

test("retryable registration failures preserve the invitation", () => {
  for (const status of [400, 403, 409, 503]) {
    const view = registrationPage();
    assert.equal(view.page.handleInvitationProblem(new view.ApiError(status)), undefined);
    assert.equal(view.url.hash, "#invitation=secret&keep=yes");
    assert.deepEqual(view.events, []);
  }
});

test("successful registration removes the invitation before navigating away", () => {
  const view = registrationPage();
  view.page.clearInvitationFragment();
  assert.equal(view.url.href, "https://panel.example.com/account-registration?source=team#keep=yes");
});

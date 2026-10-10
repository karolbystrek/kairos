import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

function renderSession(session) {
  const redirects = [];
  const exports = {};
  const source = ts.transpileModule(
    readFileSync(new URL("../components/landing-session-redirect.tsx", import.meta.url), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } },
  ).outputText;
  new Function("require", "exports", source)(name => {
    if (name === "react") return { useEffect: effect => effect() };
    if (name === "next/navigation") return { useRouter: () => ({ replace: path => redirects.push(path) }) };
    if (name === "swr") return { default: () => session };
    if (name === "@/src/api/authentication") return { getCurrentAccount() {} };
    if (name === "@/src/api/cache-keys") return { currentAccountKey: ["authentication", "current-account"] };
    return require(name);
  }, exports);
  exports.LandingSessionRedirect();
  return redirects;
}

test("landing visitors with a confirmed session go to the dashboard", () => {
  assert.deepEqual(renderSession({ data: { accountId: "staff" } }), ["/dashboard"]);
});

test("the landing remains usable while signed out, loading, or authentication is unavailable", () => {
  for (const session of [{}, { isLoading: true }, { error: new Error("Offline") }, { data: { accountId: "staff" }, error: new Error("Expired session") }]) {
    assert.deepEqual(renderSession(session), []);
  }
});

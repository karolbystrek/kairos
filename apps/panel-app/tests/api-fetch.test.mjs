import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

function client(fetch) {
  const modules = new Map();
  function load(name) {
    if (modules.has(name)) return modules.get(name);
    const exports = {};
    modules.set(name, exports);
    const dependencies = (dependency) => {
      if (dependency.includes("public-environment"))
        return { apiBaseUrl: "https://api.example.com" };
      if (dependency.startsWith("./")) return load(dependency);

      return require(dependency);
    };
    const source = ts.transpileModule(
      readFileSync(new URL(`../src/api/${name.slice(2)}.ts`, import.meta.url), "utf8"),
      { compilerOptions: { module: ts.ModuleKind.CommonJS } },
    ).outputText;
    new Function("require", "exports", "fetch", "console", source)(
      dependencies, exports, fetch, { error() {} },
    );
    return exports;
  }
  const exports = load("./api-fetch");
  exports.authentication = load("./authentication");
  exports.authState = load("./auth-state");

  return exports;
}

test("expired sessions notify authentication listeners without request replay", async () => {
  const calls = [];
  const api = client(async (url) => {
    calls.push(url);
    return new Response(null, { status: 401 });
  });
  let authenticationRequired = false;
  const unsubscribe = api.authState.subscribeToAuthenticationRequired(() => {
    authenticationRequired = true;
  });
  try {
    await assert.rejects(api.apiFetch("/api/orders/v1"), (error) => error.status === 401);
    assert.equal(authenticationRequired, true);
  } finally {
    unsubscribe();
  }
  assert.equal(calls.length, 1);
  assert.ok(calls[0].endsWith("/api/orders/v1"));
});

test("CSRF rejection retries once after retrieving a fresh token", async () => {
  for (const recovered of [true, false]) {
    let tokens = 0;
    let writes = 0;
    const api = client(async (url, init) => {
      if (url.endsWith("/csrf")) return Response.json({ token: `csrf-${++tokens}` });
      assert.equal(init.credentials, "include");
      writes++;
      assert.equal(init.headers.get("X-XSRF-TOKEN"), `csrf-${writes}`);
      if (writes === 1 || !recovered)
        return Response.json({ type: "urn:kairos:problem:csrf-token-invalid" }, { status: 403 });
      return new Response(null, { status: 204 });
    });
    if (recovered) await api.apiFetch("/api/orders/v1", { method: "POST" });
    else await assert.rejects(api.apiFetch("/api/orders/v1", { method: "POST" }),
      (error) => error.status === 403);
    assert.equal(writes, 2);
    assert.equal(tokens, 2);
  }
});

for (const operation of ["login", "logout", "changePassword"]) {
  test(`${operation} keeps success when the next CSRF bootstrap is unavailable`, async () => {
    let tokens = 0;
    let mutations = 0;
    const account = {
      accountId: "12345678-1234-4234-8234-123456789abc",
      email: "staff@example.com",
      tenantId: "12345678-1234-4234-8234-123456789abd",
      tenantRole: "ADMIN", assignment: null, capabilities: [],
    };
    const api = client(async (url, init) => {
      if (url.endsWith("/csrf")) {
        if (++tokens === 2) throw new Error("Network unavailable");
        return Response.json({ token: `csrf-${tokens}` });
      }
      mutations++;
      if (url.endsWith("/orders/v1"))
        assert.equal(init.headers.get("X-XSRF-TOKEN"), "csrf-3");
      return url.endsWith("/login") ? Response.json(account) : new Response(null, { status: 204 });
    });
    await api.initializeCsrf();
    const result = await api.authentication[operation](
      ...(operation === "login" ? [{ email: "staff@example.com", password: "password" }]
        : operation === "changePassword" ? ["current", "replacement", "replacement"] : []),
    );
    if (operation === "login") assert.deepEqual(result, account);
    if (operation === "logout") assert.equal(result, true);
    assert.equal(tokens, 1);
    assert.equal(mutations, 1);
    await assert.rejects(api.apiFetch("/api/orders/v1", { method: "POST" }));
    assert.equal(mutations, 1);
    await api.apiFetch("/api/orders/v1", { method: "POST" });
    assert.equal(mutations, 2);
  });
}

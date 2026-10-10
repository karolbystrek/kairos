import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);

function control(onConfirm = () => {}) {
  const hooks = [];
  const effects = [];
  const timers = new Map();
  const listeners = new Map();
  let cursor = 0;
  let now = 0;
  let nextTimer = 0;
  const exports = {};
  const window = {
    setTimeout(callback, delay) { const id = ++nextTimer; timers.set(id, { callback, at: now + delay }); return id; },
    clearTimeout(id) { timers.delete(id); },
    addEventListener(name, callback) { listeners.set(name, callback); },
    removeEventListener(name) { listeners.delete(name); },
  };
  const source = ts.transpileModule(readFileSync(new URL("../components/hold-to-confirm-button.tsx", import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  new Function("require", "exports", "window", source)(name => {
    if (name === "@heroui/react") return { Button: "Button" };
    if (name === "react") return {
      useId() { return "hold-instructions"; },
      useRef(initial) { const index = cursor++; return hooks[index] ??= { current: initial }; },
      useState(initial) { const index = cursor++; if (!(index in hooks)) hooks[index] = initial; return [hooks[index], value => { hooks[index] = value; }]; },
      useEffect(effect, dependencies) {
        const index = cursor++;
        const previous = hooks[index];
        if (!previous || dependencies.some((value, i) => value !== previous[i])) {
          effects[index]?.(); effects[index] = effect(); hooks[index] = dependencies;
        }
      },
    };
    return require(name);
  }, exports, window);
  const target = {
    setPointerCapture() {}, hasPointerCapture() { return true; }, releasePointerCapture() {},
    getBoundingClientRect() { return { left: 0, right: 100, top: 0, bottom: 40 }; },
  };
  const pointer = { isPrimary: true, button: 0, pointerId: 1, clientX: 20, clientY: 20, currentTarget: target };
  function render(props = {}) { cursor = 0; return exports.HoldToConfirmButton({ children: "Przytrzymaj, aby usunąć", onConfirm, ...props }).props.children[0].props; }
  return {
    render, pointer,
    advance(ms) { now += ms; for (const [id, timer] of timers) if (timer.at <= now) { timers.delete(id); timer.callback(); } },
    unmount() { effects.forEach(cleanup => cleanup?.()); },
    blurWindow() { listeners.get("blur")?.(); },
    releaseOutside() { listeners.get("pointerup")?.({ pointerId: 1 }); },
  };
}

test("early release or leaving the button cancels; a complete hold confirms once", async () => {
  let confirmations = 0;
  const view = control(() => { confirmations++; });
  let button = view.render();
  button.onPointerDown(view.pointer);
  view.advance(1599);
  assert.equal(confirmations, 0);
  button.onPointerUp(view.pointer);
  view.advance(1);
  assert.equal(confirmations, 0);
  button = view.render();
  button.onPointerDown(view.pointer);
  button.onPointerMove({ ...view.pointer, clientX: 120 });
  view.advance(1600);
  assert.equal(confirmations, 0);
  button = view.render();
  button.onPointerDown(view.pointer);
  view.advance(1600);
  assert.equal(confirmations, 1);
  button.onPointerDown(view.pointer);
  view.advance(1600);
  assert.equal(confirmations, 1);
  await Promise.resolve();
});

test("keyboard holds cancel on release and blur; disabled and unmounted controls never confirm", () => {
  let confirmations = 0;
  const view = control(() => { confirmations++; });
  const key = { key: " ", repeat: false, preventDefault() {} };
  let button = view.render();
  button.onKeyDown(key);
  view.advance(800);
  button.onKeyUp(key);
  view.advance(800);
  assert.equal(confirmations, 0);
  button.onKeyDown(key);
  view.blurWindow();
  view.advance(1600);
  assert.equal(confirmations, 0);
  button = view.render({ isDisabled: true });
  button.onKeyDown(key);
  view.advance(1600);
  assert.equal(confirmations, 0);
  button = view.render();
  button.onKeyDown(key);
  view.unmount();
  view.advance(1600);
  assert.equal(confirmations, 0);
});

test("pending submission blocks repeats and a failed request permits a fresh hold", async () => {
  let confirmations = 0;
  let finish;
  const view = control(() => { confirmations++; return new Promise(resolve => { finish = resolve; }); });
  let button = view.render();
  button.onPointerDown(view.pointer);
  view.advance(1600);
  button = view.render();
  button.onPointerDown(view.pointer);
  view.advance(1600);
  assert.equal(confirmations, 1);
  finish(false);
  await Promise.resolve();
  await Promise.resolve();
  button = view.render();
  button.onPointerDown(view.pointer);
  view.advance(1600);
  assert.equal(confirmations, 2);
  finish(false);
});

test("Enter confirms after the full duration; disabling mid-hold cancels", async () => {
  let confirmations = 0;
  const view = control(() => { confirmations++; });
  const key = { key: "Enter", repeat: false, preventDefault() {} };
  view.render().onKeyDown(key);
  view.advance(1599);
  assert.equal(confirmations, 0);
  view.advance(1);
  assert.equal(confirmations, 1);
  await Promise.resolve();
  view.render().onPointerDown(view.pointer);
  view.advance(800);
  view.render({ isDisabled: true });
  view.advance(800);
  assert.equal(confirmations, 1);
});

test("assistive activation requires an explicit second confirmation and blur cancels it", () => {
  let confirmations = 0;
  const view = control(() => { confirmations++; });
  view.render().onPress({ pointerType: "virtual" });
  assert.equal(confirmations, 0);
  view.render().onBlur();
  view.render().onPress({ pointerType: "virtual" });
  assert.equal(confirmations, 0);
  view.render().onPress({ pointerType: "virtual" });
  assert.equal(confirmations, 1);
});

test("leaving the button or releasing outside cancels without pointer capture", () => {
  let confirmations = 0;
  const view = control(() => { confirmations++; });
  view.render().onPointerDown(view.pointer);
  view.render().onPointerLeave(view.pointer);
  view.advance(1600);
  assert.equal(confirmations, 0);
  view.render().onPointerDown(view.pointer);
  view.releaseOutside();
  view.advance(1600);
  assert.equal(confirmations, 0);
});

test("the real HeroUI button preserves linked Polish keyboard instructions", async () => {
  const { Button } = await import("@heroui/react");
  const React = require("react");
  const { renderToStaticMarkup } = require("react-dom/server");
  const exports = {};
  const source = ts.transpileModule(readFileSync(new URL("../components/hold-to-confirm-button.tsx", import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  new Function("require", "exports", source)(name => name === "@heroui/react" ? { Button } : require(name), exports);
  const html = renderToStaticMarkup(React.createElement(exports.HoldToConfirmButton, { onConfirm() {}, children: "Przytrzymaj, aby usunąć" }));
  const describedBy = /aria-describedby="([^"]+)"/.exec(html)?.[1];
  assert.ok(describedBy, "Button must reference its instruction text");
  assert.ok(html.includes(`id="${describedBy}"`));
  assert.ok(html.includes("klawisz spacji albo Enter przez 1,6 sekundy"));
});

test("re-enabling after interruption clears stale progress and assistive confirmation", () => {
  let confirmations = 0;
  const view = control(() => { confirmations++; });
  view.render().onPointerDown(view.pointer);
  view.render({ isDisabled: true });
  assert.equal(view.render()["data-holding"], undefined);
  view.render().onPress({ pointerType: "virtual" });
  view.render({ isDisabled: true });
  view.render().onPress({ pointerType: "virtual" });
  assert.equal(confirmations, 0);
});

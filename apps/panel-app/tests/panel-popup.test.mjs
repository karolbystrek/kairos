import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
const Modal = Object.assign(() => {}, Object.fromEntries(
  ["Backdrop", "Container", "Dialog", "CloseTrigger", "Header", "Heading", "Body", "Footer"]
    .map(name => [name, `Modal.${name}`]),
));
const exports = {};
const source = ts.transpileModule(
  readFileSync(new URL("../components/panel-popup.tsx", import.meta.url), "utf8"),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } },
).outputText;
new Function("require", "exports", source)(name => name === "@heroui/react"
  ? { Modal, AlertDialog: { Icon: "Icon" } } : require(name), exports);

// Verify the app's dismissal policy at its HeroUI boundary; HeroUI owns DOM events/focus.
for (const role of ["dialog", "alertdialog"]) {
  test(`${role} provides Close and permits outside-click and Escape dismissal`, () => {
    const changes = [];
    const popup = exports.PanelPopup({ isOpen: true, role, onOpenChange: open => changes.push(open) });
    const backdrop = popup.props.children;
    const dialog = backdrop.props.children.props.children;
    assert.equal(backdrop.props.isDismissable, true);
    assert.equal(backdrop.props.isKeyboardDismissDisabled, false);
    assert.equal(dialog.props.role, role);
    assert.equal(dialog.props.children[0].props["aria-label"], "Close");
    popup.props.onOpenChange(false);
    assert.deepEqual(changes, [false]);
  });
}

test("required onboarding has no Close and rejects dismissal", () => {
  const changes = [];
  const popup = exports.PanelPopup({ isOpen: true, isRequired: true, onOpenChange: open => changes.push(open) });
  const backdrop = popup.props.children;
  const dialog = backdrop.props.children.props.children;
  assert.equal(backdrop.props.isDismissable, false);
  assert.equal(backdrop.props.isKeyboardDismissDisabled, true);
  assert.equal(dialog.props.children[0], false);
  popup.props.onOpenChange(false);
  assert.deepEqual(changes, []);
});

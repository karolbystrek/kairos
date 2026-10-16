import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";

import { HoldToConfirmButton } from "@/components/hold-to-confirm-button";

const pointer = { isPrimary: true, button: 0, pointerId: 1, clientX: 20, clientY: 20 };
const space = { key: " " };
let onConfirm: ReturnType<typeof vi.fn<() => void | Promise<unknown>>>;

beforeEach(() => {
  vi.useFakeTimers();
  onConfirm = vi.fn();
});

function setup(props: { isDisabled?: boolean } = {}) {
  const view = render(<HoldToConfirmButton onConfirm={onConfirm} {...props}>Przytrzymaj, aby usunąć</HoldToConfirmButton>);
  const button = screen.getByRole("button");

  vi.spyOn(button, "getBoundingClientRect").mockReturnValue({ left: 0, right: 100, top: 0, bottom: 40 } as DOMRect);

  return {
    button,
    view,
    rerender: (next: { isDisabled?: boolean } = {}) =>
      view.rerender(<HoldToConfirmButton onConfirm={onConfirm} {...next}>Przytrzymaj, aby usunąć</HoldToConfirmButton>),
  };
}

const advance = (ms: number) => act(() => vi.advanceTimersByTime(ms));
// Assistive technology activates the button with a click that has no preceding pointer events.
const virtualPress = (button: HTMLElement) => fireEvent.click(button, { detail: 0 });

it("early release or leaving the button cancels; a complete hold confirms once", async () => {
  const { button } = setup();

  fireEvent.pointerDown(button, pointer);
  advance(1599);
  fireEvent.pointerUp(button, pointer);
  advance(1);
  expect(onConfirm).not.toHaveBeenCalled();
  fireEvent.pointerDown(button, pointer);
  fireEvent.pointerMove(button, { ...pointer, clientX: 120 });
  advance(1600);
  expect(onConfirm).not.toHaveBeenCalled();
  fireEvent.pointerDown(button, pointer);
  advance(1600);
  expect(onConfirm).toHaveBeenCalledOnce();
  await act(async () => {});
  fireEvent.pointerUp(button, pointer);
  fireEvent.pointerDown(button, pointer);
  advance(1600);
  expect(onConfirm).toHaveBeenCalledTimes(2);
});

it("keyboard holds cancel on release and blur; disabled and unmounted controls never confirm", () => {
  const { button, rerender, view } = setup();

  fireEvent.keyDown(button, space);
  advance(800);
  fireEvent.keyUp(button, space);
  advance(800);
  fireEvent.keyDown(button, space);
  fireEvent.blur(window);
  advance(1600);
  rerender({ isDisabled: true });
  fireEvent.keyDown(button, space);
  advance(1600);
  rerender();
  fireEvent.keyDown(button, space);
  view.unmount();
  advance(1600);
  expect(onConfirm).not.toHaveBeenCalled();
});

it("pending submission blocks repeats and a failed request permits a fresh hold", async () => {
  let finish = (_: boolean) => {};

  onConfirm.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
  const { button } = setup();

  fireEvent.pointerDown(button, pointer);
  advance(1600);
  fireEvent.pointerUp(button, pointer);
  fireEvent.pointerDown(button, pointer);
  advance(1600);
  expect(onConfirm).toHaveBeenCalledOnce();
  await act(async () => finish(false));
  fireEvent.pointerUp(button, pointer);
  fireEvent.pointerDown(button, pointer);
  advance(1600);
  expect(onConfirm).toHaveBeenCalledTimes(2);
});

it("Enter confirms after the full duration; disabling mid-hold cancels", async () => {
  const { button, rerender } = setup();

  fireEvent.keyDown(button, { key: "Enter" });
  advance(1599);
  expect(onConfirm).not.toHaveBeenCalled();
  advance(1);
  expect(onConfirm).toHaveBeenCalledOnce();
  await act(async () => {});
  fireEvent.keyUp(button, { key: "Enter" });
  fireEvent.pointerDown(button, pointer);
  advance(800);
  rerender({ isDisabled: true });
  advance(800);
  expect(onConfirm).toHaveBeenCalledOnce();
});

it("assistive activation requires an explicit second confirmation and blur cancels it", () => {
  const { button } = setup();

  virtualPress(button);
  expect(button.textContent).toContain("Potwierdź operację");
  fireEvent.blur(button);
  expect(button.textContent).toContain("Przytrzymaj, aby usunąć");
  virtualPress(button);
  expect(onConfirm).not.toHaveBeenCalled();
  virtualPress(button);
  expect(onConfirm).toHaveBeenCalledOnce();
});

it("leaving the button or releasing outside cancels without pointer capture", () => {
  const { button } = setup();

  fireEvent.pointerDown(button, pointer);
  fireEvent.pointerLeave(button, pointer);
  advance(1600);
  fireEvent.pointerDown(button, pointer);
  fireEvent.pointerUp(window, { pointerId: 1 });
  advance(1600);
  expect(onConfirm).not.toHaveBeenCalled();
});

it("the real HeroUI button links its Polish keyboard instructions", () => {
  const { button } = setup();
  const instructions = document.getElementById(button.getAttribute("aria-describedby") ?? "");

  expect(instructions?.textContent).toContain("klawisz spacji albo Enter przez 1,6 sekundy");
});

it("re-enabling after interruption clears stale progress and assistive confirmation", () => {
  const { button, rerender } = setup();

  fireEvent.pointerDown(button, pointer);
  expect(button.hasAttribute("data-holding")).toBe(true);
  rerender({ isDisabled: true });
  rerender();
  expect(button.hasAttribute("data-holding")).toBe(false);
  virtualPress(button);
  rerender({ isDisabled: true });
  rerender();
  virtualPress(button);
  expect(onConfirm).not.toHaveBeenCalled();
});

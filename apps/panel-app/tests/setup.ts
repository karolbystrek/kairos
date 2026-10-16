import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// jsdom has no PointerEvent, so pointer fields such as pointerId would be dropped.
class PointerEvent extends MouseEvent {
  readonly pointerId: number;
  readonly pointerType: string;
  readonly isPrimary: boolean;
  constructor(type: string, init: PointerEventInit = {}) {
    super(type, init);
    this.pointerId = init.pointerId ?? 0;
    this.pointerType = init.pointerType ?? "";
    this.isPrimary = init.isPrimary ?? false;
  }
}

globalThis.PointerEvent ??= PointerEvent as typeof globalThis.PointerEvent;

// jsdom has no CSS namespace; React Aria escapes collection keys for selectors.
globalThis.CSS ??= { escape: (value: string) => value.replace(/[^\w-]/g, "\\$&") } as typeof CSS;

// jsdom runs no animations; HeroUI waits on them when tabs and popovers change.
Element.prototype.getAnimations ??= () => [];

// jsdom has no layout; observed elements report one fixed size so charts can draw.
globalThis.ResizeObserver ??= class {
  constructor(private callback: ResizeObserverCallback) {}
  observe(target: Element) {
    const contentRect = { width: 800, height: 400, top: 0, left: 0, right: 800, bottom: 400, x: 0, y: 0 };

    this.callback([{ target, contentRect } as unknown as ResizeObserverEntry], this);
  }
  unobserve() {}
  disconnect() {}
};

// jsdom has no EventSource; components that stream orders get an idle connection.
globalThis.EventSource ??= class {
  addEventListener() {}
  close() {}
} as unknown as typeof EventSource;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

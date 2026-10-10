"use client";

import type { ComponentProps, PointerEvent, ReactNode } from "react";

import { Button } from "@heroui/react";
import { useEffect, useId, useRef, useState } from "react";

export function HoldToConfirmButton({
  children,
  className = "",
  isDisabled = false,
  isPending = false,
  onConfirm,
  variant = "danger",
}: {
  children: ReactNode;
  className?: string;
  isDisabled?: boolean;
  isPending?: boolean;
  onConfirm: () => void | Promise<unknown>;
  variant?: ComponentProps<typeof Button>["variant"];
}) {
  const instructionsId = useId();
  const [holding, setHolding] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [virtualArmed, setVirtualArmed] = useState(false);
  const timer = useRef<number | undefined>(undefined);
  const input = useRef<number | "keyboard" | undefined>(undefined);
  const busy = useRef(false);
  const latest = useRef({ isDisabled, isPending, onConfirm });

  if ((isDisabled || isPending) && (holding || virtualArmed)) {
    setHolding(false);
    setVirtualArmed(false);
  }

  useEffect(() => {
    latest.current = { isDisabled, isPending, onConfirm };
  }, [isDisabled, isPending, onConfirm]);

  function cancel() {
    window.clearTimeout(timer.current);
    timer.current = undefined;
    input.current = undefined;
    setHolding(false);
  }

  async function confirm() {
    if (busy.current || latest.current.isDisabled || latest.current.isPending)
      return;
    cancel();
    busy.current = true;
    setSubmitting(true);
    setVirtualArmed(false);
    try {
      await latest.current.onConfirm();
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  function start(source: number | "keyboard") {
    if (
      busy.current ||
      latest.current.isDisabled ||
      latest.current.isPending ||
      input.current !== undefined
    )
      return;
    input.current = source;
    setHolding(true);
    timer.current = window.setTimeout(() => void confirm(), 1600);
  }

  useEffect(() => {
    function interrupt() {
      window.clearTimeout(timer.current);
      timer.current = undefined;
      input.current = undefined;
      setHolding(false);
      setVirtualArmed(false);
    }
    function interruptPointer(event: globalThis.PointerEvent) {
      if (
        typeof input.current === "number" &&
        input.current === event.pointerId
      )
        interrupt();
    }
    window.addEventListener("blur", interrupt);
    window.addEventListener("pointerup", interruptPointer);
    window.addEventListener("pointercancel", interruptPointer);

    return () => {
      window.clearTimeout(timer.current);
      input.current = undefined;
      window.removeEventListener("blur", interrupt);
      window.removeEventListener("pointerup", interruptPointer);
      window.removeEventListener("pointercancel", interruptPointer);
    };
  }, []);

  useEffect(() => {
    if (isDisabled || isPending) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
      input.current = undefined;
    }
  }, [isDisabled, isPending]);

  function endPointer(event: PointerEvent<HTMLButtonElement>) {
    if (input.current !== event.pointerId) return;
    cancel();
  }

  return (
    <>
      <Button
        aria-describedby={instructionsId}
        className={`hold-to-confirm ${className}`}
        data-holding={(holding && !isDisabled && !isPending) || undefined}
        isDisabled={isDisabled}
        isPending={isPending || submitting}
        type="button"
        variant={variant}
        onBlur={() => {
          cancel();
          setVirtualArmed(false);
        }}
        onKeyDown={(event) => {
          if (event.key !== " " && event.key !== "Enter") return;
          event.preventDefault();
          if (!event.repeat) start("keyboard");
        }}
        onKeyUp={(event) => {
          if (event.key !== " " && event.key !== "Enter") return;
          event.preventDefault();
          if (input.current === "keyboard") cancel();
        }}
        onPointerCancel={endPointer}
        onPointerDown={(event) => {
          if (
            !event.isPrimary ||
            event.button !== 0 ||
            isDisabled ||
            isPending ||
            busy.current
          )
            return;
          start(event.pointerId);
        }}
        onPointerLeave={endPointer}
        onPointerMove={(event) => {
          if (input.current !== event.pointerId) return;
          const rect = event.currentTarget.getBoundingClientRect();

          if (
            event.clientX < rect.left ||
            event.clientX > rect.right ||
            event.clientY < rect.top ||
            event.clientY > rect.bottom
          )
            endPointer(event);
        }}
        onPointerUp={endPointer}
        onPress={(event) => {
          // Assistive activation has no hold events, so require two deliberate activations.
          if (event.pointerType !== "virtual") return;
          if (virtualArmed) void confirm();
          else setVirtualArmed(true);
        }}
      >
        <span aria-hidden="true" className="hold-to-confirm__fill" />
        <span className="relative flex items-center justify-center gap-2">
          {virtualArmed ? "Potwierdź operację" : children}
        </span>
      </Button>
      <span className="sr-only" id={instructionsId}>
        Przytrzymaj przycisk lub klawisz spacji albo Enter przez 1,6 sekundy.
        Zwolnienie anuluje operację. Z czytnikiem ekranu aktywuj przycisk, a
        następnie aktywuj „Potwierdź operację”.
      </span>
    </>
  );
}

"use client";

import { ToggleButton, Tooltip } from "@heroui/react";
import { MoonIcon, SunIcon } from "@phosphor-icons/react";
import { useTheme } from "next-themes";
import { useSyncExternalStore } from "react";

const subscribe = () => () => {};

export function ThemeToggle({ className = "" }: { className?: string }) {
  const { resolvedTheme, setTheme } = useTheme();
  // The stored theme is unknown during SSR; the icon and colours follow the
  // `.dark` class in CSS, so only the pressed state waits for hydration.
  const isHydrated = useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
  const isDark = isHydrated && resolvedTheme === "dark";

  return (
    <Tooltip delay={500}>
      <Tooltip.Trigger className="flex">
        <ToggleButton
          isIconOnly
          aria-label="Tryb ciemny"
          className={`theme-toggle ${className}`}
          isSelected={isDark}
          onChange={(selected) => setTheme(selected ? "dark" : "light")}
        >
          <SunIcon className="theme-toggle__sun" size={20} />
          <MoonIcon className="theme-toggle__moon" size={20} weight="fill" />
        </ToggleButton>
      </Tooltip.Trigger>
      <Tooltip.Content>Tryb ciemny</Tooltip.Content>
    </Tooltip>
  );
}

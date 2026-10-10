"use client";

import { Dropdown, Tooltip } from "@heroui/react";
import { Sun } from "lucide-react";
import { useTheme } from "next-themes";

export function PanelAppearanceMenu() {
  const { setTheme, theme = "system" } = useTheme();

  return (
    <Dropdown>
      <Tooltip delay={500}>
        <Tooltip.Trigger>
          <Dropdown.Trigger
            aria-label="Wygląd"
            className="icon-menu-trigger icon-menu-trigger--touch"
          >
            <Sun aria-hidden="true" size={20} />
          </Dropdown.Trigger>
        </Tooltip.Trigger>
        <Tooltip.Content>Wygląd</Tooltip.Content>
      </Tooltip>
      <Dropdown.Popover placement="bottom end">
        <Dropdown.Menu
          aria-label="Wygląd"
          selectedKeys={new Set([theme])}
          selectionMode="single"
          onAction={(key) => setTheme(String(key))}
        >
          <Dropdown.Item id="system">Systemowy</Dropdown.Item>
          <Dropdown.Item id="light">Jasny</Dropdown.Item>
          <Dropdown.Item id="dark">Ciemny</Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  );
}

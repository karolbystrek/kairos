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
            aria-label="Appearance"
            className="icon-menu-trigger icon-menu-trigger--touch"
          >
            <Sun aria-hidden="true" size={20} />
          </Dropdown.Trigger>
        </Tooltip.Trigger>
        <Tooltip.Content>Appearance</Tooltip.Content>
      </Tooltip>
      <Dropdown.Popover placement="bottom end">
        <Dropdown.Menu
          aria-label="Appearance"
          selectedKeys={new Set([theme])}
          selectionMode="single"
          onAction={(key) => setTheme(String(key))}
        >
          <Dropdown.Item id="system">System</Dropdown.Item>
          <Dropdown.Item id="light">Light</Dropdown.Item>
          <Dropdown.Item id="dark">Dark</Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  );
}

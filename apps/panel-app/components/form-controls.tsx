"use client";

import type { ComponentProps, ReactNode } from "react";

import { Input, Label, Select, TextField } from "@heroui/react";

export function FormTextField({
  label,
  placeholder = label,
  inputProps,
  ...props
}: Omit<ComponentProps<typeof TextField>, "children"> & {
  label: string;
  placeholder?: string;
  inputProps?: Omit<ComponentProps<typeof Input>, "placeholder">;
}) {
  return (
    <TextField {...props}>
      <Label className="sr-only">{label}</Label>
      <Input {...inputProps} placeholder={placeholder} />
    </TextField>
  );
}

export function FormSelect({
  label,
  placeholder = `Select ${label.toLowerCase()}`,
  children,
  ...props
}: Omit<ComponentProps<typeof Select>, "children"> & {
  label: string;
  placeholder?: string;
  children: ReactNode;
}) {
  return (
    <Select {...props} placeholder={placeholder}>
      <Label className="sr-only">{label}</Label>
      <Select.Trigger className="min-w-0 max-w-full">
        <Select.Value className="min-w-0" />
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover>{children}</Select.Popover>
    </Select>
  );
}

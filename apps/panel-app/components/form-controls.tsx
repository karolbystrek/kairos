"use client";

import type { ComponentProps, ReactNode } from "react";

import { FieldError, Input, Label, Select, TextField } from "@heroui/react";

export function FormTextField({
  label,
  placeholder = label,
  inputProps,
  errorMessage,
  ...props
}: Omit<ComponentProps<typeof TextField>, "children"> & {
  errorMessage?: string;
  label: string;
  placeholder?: string;
  inputProps?: Omit<ComponentProps<typeof Input>, "placeholder">;
}) {
  return (
    <TextField {...props} isInvalid={Boolean(errorMessage) || props.isInvalid}>
      <Label className="sr-only">{label}</Label>
      <Input {...inputProps} placeholder={placeholder} />
      {errorMessage && <FieldError>{errorMessage}</FieldError>}
    </TextField>
  );
}

export function FormSelect({
  label,
  placeholder = `Wybierz: ${label.toLocaleLowerCase("pl-PL")}`,
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

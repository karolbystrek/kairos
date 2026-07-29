import type { FormEvent } from "react";

import { Alert, Button, Input, Label, TextField } from "@heroui/react";
import { useState } from "react";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import { ApiError } from "@/src/api/api-fetch";
import {
  registerTenant,
  type TenantRegistration,
  type TenantRegistrationInput,
} from "@/src/api/tenant-registrations";

const tenantRegistrationKey = ["tenant-registration"] as const;

function registrationMutation(
  _key: typeof tenantRegistrationKey,
  { arg }: { arg: TenantRegistrationInput },
): Promise<TenantRegistration> {
  return registerTenant(arg);
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "The submitted values are not valid.";
  }

  if (error instanceof ApiError) {
    if (error.status === 409) {
      return "That username or email is already registered.";
    }

    return error.message;
  }

  return "Registration could not be completed. Check your connection and try again.";
}

export function TenantRegistrationForm({
  onRegistered,
}: {
  onRegistered: (registration: TenantRegistration) => void;
}) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");

  const {
    error,
    isMutating,
    reset,
    trigger: triggerRegistration,
  } = useSWRMutation(tenantRegistrationKey, registrationMutation, {
    throwOnError: false,
  });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isMutating) return;

    reset();
    const registration = await triggerRegistration({
      username,
      email,
      password,
      passwordConfirmation,
    });

    if (!registration) return;

    setPassword("");
    setPasswordConfirmation("");
    onRegistered(registration);
  }

  return (
    <div className="flex flex-col gap-5">
      {error && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Registration failed</Alert.Title>
            <Alert.Description>{getErrorMessage(error)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      <form className="flex flex-col gap-4" onSubmit={submit}>
        <TextField
          fullWidth
          isRequired
          isDisabled={isMutating}
          maxLength={120}
          name="username"
          value={username}
          onChange={setUsername}
        >
          <Label>Administrator username</Label>
          <Input
            autoCapitalize="none"
            autoComplete="username"
            spellCheck={false}
          />
        </TextField>

        <TextField
          fullWidth
          isRequired
          isDisabled={isMutating}
          maxLength={254}
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
        >
          <Label>Administrator email</Label>
          <Input
            autoCapitalize="none"
            autoComplete="email"
            spellCheck={false}
          />
        </TextField>

        <TextField
          fullWidth
          isRequired
          isDisabled={isMutating}
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
        >
          <Label>Password</Label>
          <Input autoComplete="new-password" />
        </TextField>

        <TextField
          fullWidth
          isRequired
          isDisabled={isMutating}
          name="passwordConfirmation"
          type="password"
          value={passwordConfirmation}
          onChange={setPasswordConfirmation}
        >
          <Label>Confirm password</Label>
          <Input autoComplete="new-password" />
        </TextField>

        <Button fullWidth isPending={isMutating} type="submit">
          {isMutating ? "Registering…" : "Register tenant"}
        </Button>
      </form>
    </div>
  );
}

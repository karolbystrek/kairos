import type { FormEvent } from "react";
import type { CurrentAccount } from "@/src/api/authentication";

import {
  Alert,
  Button,
  Chip,
  Input,
  Label,
  Spinner,
  TextField,
} from "@heroui/react";
import { useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";
import { ZodError } from "zod";

import {
  provisionAccount,
  type AssignmentRole,
  type ManagedAccount,
  type ProvisionAccountInput,
} from "@/src/api/accounts";
import { ApiError } from "@/src/api/api-fetch";
import { staffCachePrefix, staffLocationsKey } from "@/src/api/cache-keys";
import { listLocations } from "@/src/api/orders";

type ProvisionMutationInput = {
  locationId: string;
  account: ProvisionAccountInput;
};

const accountProvisioningKey = (accountId: string) =>
  [staffCachePrefix, accountId, "account-provisioning"] as const;

function provisionAccountMutation(
  _key: ReturnType<typeof accountProvisioningKey>,
  { arg }: { arg: ProvisionMutationInput },
): Promise<ManagedAccount> {
  return provisionAccount(arg.locationId, arg.account);
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "The submitted values are not valid.";
  }

  return error instanceof Error
    ? error.message
    : "An unexpected error occurred.";
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

export function AccountManagement({ account }: { account: CurrentAccount }) {
  const isAdministrator = account.tenantRole === "ADMIN";
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const [role, setRole] = useState<AssignmentRole>("OPERATOR");
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [createdAccount, setCreatedAccount] = useState<ManagedAccount>();

  const {
    data: locations = [],
    error: locationsError,
    isLoading: areLocationsLoading,
  } = useSWR(staffLocationsKey(account.accountId), () => listLocations(), {
    errorRetryCount: 3,
    shouldRetryOnError,
  });

  const {
    error: provisioningError,
    isMutating,
    reset,
    trigger: triggerProvisioning,
  } = useSWRMutation(
    accountProvisioningKey(account.accountId),
    provisionAccountMutation,
    { throwOnError: false },
  );

  const assignedLocationId = account.assignment?.locationId;
  const locationId = isAdministrator
    ? locations.some((location) => location.id === selectedLocationId)
      ? selectedLocationId
      : locations[0]?.id
    : assignedLocationId;
  const selectedRole = isAdministrator ? role : "OPERATOR";
  const currentLocation = locations.find(
    (location) => location.id === locationId,
  );
  const error = locationsError ?? provisioningError;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!locationId || isMutating) return;

    reset();
    setCreatedAccount(undefined);
    const result = await triggerProvisioning({
      locationId,
      account: {
        displayName,
        username,
        email,
        password,
        role: selectedRole,
      },
    });

    if (!result) return;

    setCreatedAccount(result);
    setDisplayName("");
    setUsername("");
    setEmail("");
    setPassword("");
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-2xl font-semibold">Provision account</h2>
        <p className="text-muted">
          Create credentials for an existing tenant location.
        </p>
      </div>

      {error && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Account request failed</Alert.Title>
            <Alert.Description>{getErrorMessage(error)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {createdAccount && (
        <Alert status="success">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Account created</Alert.Title>
            <Alert.Description>
              {createdAccount.displayName} · {createdAccount.username} ·{" "}
              {createdAccount.role === "MANAGER" ? "Manager" : "Operator"}
              {createdAccount.email ? ` · ${createdAccount.email}` : ""}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {areLocationsLoading ? (
        <Spinner aria-label="Loading locations" />
      ) : locations.length === 0 || !locationId ? (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>No location available</Alert.Title>
            <Alert.Description>
              Account provisioning requires an accessible location.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      ) : (
        <>
          <section className="flex flex-col gap-3">
            <h3 className="text-lg font-semibold">Location</h3>
            {isAdministrator ? (
              <div className="flex flex-wrap gap-2">
                {locations.map((location) => (
                  <Button
                    key={location.id}
                    variant={
                      location.id === locationId ? "primary" : "secondary"
                    }
                    onPress={() => setSelectedLocationId(location.id)}
                  >
                    {location.name}
                  </Button>
                ))}
              </div>
            ) : (
              <Chip>
                {currentLocation?.name ?? account.assignment?.locationName}
              </Chip>
            )}
          </section>

          <section className="flex flex-col gap-3">
            <h3 className="text-lg font-semibold">Role</h3>
            {isAdministrator ? (
              <div className="flex flex-wrap gap-2">
                {(["MANAGER", "OPERATOR"] as const).map((availableRole) => (
                  <Button
                    key={availableRole}
                    variant={
                      availableRole === selectedRole ? "primary" : "secondary"
                    }
                    onPress={() => setRole(availableRole)}
                  >
                    {availableRole === "MANAGER" ? "Manager" : "Operator"}
                  </Button>
                ))}
              </div>
            ) : (
              <Chip>Operator</Chip>
            )}
          </section>

          <form className="flex max-w-xl flex-col gap-4" onSubmit={submit}>
            <TextField
              fullWidth
              isRequired
              isDisabled={isMutating}
              maxLength={120}
              name="displayName"
              value={displayName}
              onChange={setDisplayName}
            >
              <Label>Display name</Label>
              <Input autoComplete="off" />
            </TextField>

            <TextField
              fullWidth
              isRequired
              isDisabled={isMutating}
              maxLength={120}
              name="username"
              value={username}
              onChange={setUsername}
            >
              <Label>Username</Label>
              <Input
                autoCapitalize="none"
                autoComplete="off"
                spellCheck={false}
              />
            </TextField>

            <TextField
              fullWidth
              isDisabled={isMutating}
              maxLength={254}
              name="email"
              type="email"
              value={email}
              onChange={setEmail}
            >
              <Label>Email (optional)</Label>
              <Input
                autoCapitalize="none"
                autoComplete="off"
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
              <Label>Initial password</Label>
              <Input autoComplete="new-password" />
            </TextField>

            <Button isPending={isMutating} type="submit">
              {isMutating ? "Creating…" : "Create account"}
            </Button>
          </form>
        </>
      )}
    </div>
  );
}

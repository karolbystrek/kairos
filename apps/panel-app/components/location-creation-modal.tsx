"use client";

import type { FormEvent } from "react";

import { Alert, Button, Input, Label, Modal, TextField } from "@heroui/react";
import { Plus as PlusIcon } from "lucide-react";
import { useState } from "react";
import { useSWRConfig } from "swr";
import { ZodError } from "zod";

import { ApiError } from "@/src/api/api-fetch";
import { staffLocationsKey } from "@/src/api/cache-keys";
import {
  createLocation,
  managedLocationNameSchema,
  sortLocations,
  type Location,
} from "@/src/api/locations";

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Enter a valid location name.";
  }
  if (error instanceof ApiError && error.status === 409) {
    return "A location with that name already exists.";
  }
  if (error instanceof ApiError) return error.message;

  return "The location could not be created. Check your connection and try again.";
}

export function LocationCreationModal({
  accountId,
  isOpen,
  onCreated,
  onOpenChange,
}: {
  accountId: string;
  isOpen: boolean;
  onCreated: (location: Location) => void;
  onOpenChange: (open: boolean) => void;
}) {
  const { mutate } = useSWRConfig();
  const [name, setName] = useState("");
  const [error, setError] = useState<unknown>();
  const [isCreating, setIsCreating] = useState(false);

  function changeOpen(open: boolean) {
    if (isCreating) return;
    onOpenChange(open);
    if (!open) {
      setName("");
      setError(undefined);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isCreating) return;

    setError(undefined);
    setIsCreating(true);
    try {
      const validatedName = managedLocationNameSchema.parse(name);
      const location = await createLocation(validatedName);

      await mutate(
        staffLocationsKey(accountId),
        (current: Location[] | undefined) =>
          sortLocations([
            ...(current ?? []).filter((item) => item.id !== location.id),
            location,
          ]),
        { revalidate: false },
      );
      onCreated(location);
      onOpenChange(false);
      setName("");
      void mutate(staffLocationsKey(accountId));
    } catch (caught) {
      setError(caught);
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <Modal isOpen={isOpen} onOpenChange={changeOpen}>
      <Modal.Backdrop>
        <Modal.Container placement="center" size="lg">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>New location</Modal.Heading>
            </Modal.Header>
            <form onSubmit={submit}>
              <Modal.Body className="flex flex-col gap-4">
                {error !== undefined && (
                  <Alert status="danger">
                    <Alert.Indicator />
                    <Alert.Content>
                      <Alert.Title>Location could not be created</Alert.Title>
                      <Alert.Description>
                        {getErrorMessage(error)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
                <TextField
                  fullWidth
                  isRequired
                  isDisabled={isCreating}
                  maxLength={120}
                  name="location-name"
                  value={name}
                  onChange={(value) => {
                    setName(value);
                    setError(undefined);
                  }}
                >
                  <Label>Location name</Label>
                  <Input autoComplete="organization" />
                </TextField>
              </Modal.Body>
              <Modal.Footer>
                <Button slot="close" variant="tertiary">
                  Cancel
                </Button>
                <Button isPending={isCreating} type="submit">
                  <PlusIcon size={18} />
                  {isCreating ? "Creating…" : "Create"}
                </Button>
              </Modal.Footer>
            </form>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}

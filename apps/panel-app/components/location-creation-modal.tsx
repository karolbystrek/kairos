"use client";

import type { FormEvent } from "react";

import { Alert, Button } from "@heroui/react";
import { Plus as PlusIcon } from "lucide-react";
import { useState } from "react";
import { useSWRConfig } from "swr";
import { ZodError } from "zod";

import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
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
    return error.issues[0]?.message ?? "Podaj prawidłową nazwę lokalu.";
  }
  if (error instanceof ApiError && error.status === 409) {
    return "Lokal o tej nazwie już istnieje.";
  }
  if (error instanceof ApiError) return error.message;

  return "Nie udało się utworzyć lokalu. Sprawdź połączenie i spróbuj ponownie.";
}

export function LocationCreationModal({
  accountId,
  isOpen,
  isRequired = false,
  onCreated,
  onOpenChange,
}: {
  accountId: string;
  isOpen: boolean;
  isRequired?: boolean;
  onCreated: (location: Location) => void;
  onOpenChange: (open: boolean) => void;
}) {
  const { mutate } = useSWRConfig();
  const [name, setName] = useState("");
  const [reviewUrl, setReviewUrl] = useState("");
  const [error, setError] = useState<unknown>();
  const [isCreating, setIsCreating] = useState(false);

  function changeOpen(open: boolean) {
    onOpenChange(open);
    if (!open) {
      setName("");
      setReviewUrl("");
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
      const location = await createLocation(validatedName, reviewUrl);

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
      setReviewUrl("");
      void mutate(staffLocationsKey(accountId));
    } catch (caught) {
      setError(caught);
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <PanelPopup
      isOpen={isOpen}
      isRequired={isRequired}
      size="lg"
      onOpenChange={changeOpen}
    >
      <PanelPopup.Header>
        <PanelPopup.Heading>
          {isRequired ? "Utwórz pierwszy lokal" : "Nowy lokal"}
        </PanelPopup.Heading>
      </PanelPopup.Header>
      <form onSubmit={submit}>
        <PanelPopup.Body className="flex flex-col gap-4">
          {error !== undefined && (
            <Alert status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>Nie udało się utworzyć lokalu</Alert.Title>
                <Alert.Description>{getErrorMessage(error)}</Alert.Description>
              </Alert.Content>
            </Alert>
          )}
          <FormTextField
            fullWidth
            isRequired
            inputProps={{ autoComplete: "organization" }}
            isDisabled={isCreating}
            label="Nazwa lokalu"
            maxLength={120}
            name="location-name"
            value={name}
            onChange={(value) => {
              setName(value);
              setError(undefined);
            }}
          />
          <FormTextField
            fullWidth
            inputProps={{ type: "url" }}
            isDisabled={isCreating}
            label="Link do opinii Google (opcjonalnie)"
            maxLength={2048}
            name="google-review-url"
            placeholder="Link do opinii Google (puste pole wyłącza zaproszenia)"
            value={reviewUrl}
            onChange={setReviewUrl}
          />
        </PanelPopup.Body>
        <PanelPopup.Footer>
          {!isRequired && (
            <Button aria-label="Anuluj" slot="close" variant="tertiary">
              Anuluj
            </Button>
          )}
          <Button isPending={isCreating} type="submit">
            <PlusIcon size={18} />
            {isCreating ? "Tworzenie…" : "Utwórz"}
          </Button>
        </PanelPopup.Footer>
      </form>
    </PanelPopup>
  );
}

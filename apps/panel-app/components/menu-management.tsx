"use client";

import type { FormEvent } from "react";
import type { Location } from "@/src/api/locations";

import {
  Alert,
  Button,
  Label,
  ListBox,
  TextArea,
  TextField,
  toast,
} from "@heroui/react";
import { TrashIcon as DeleteIcon } from "@phosphor-icons/react";
import { useState } from "react";
import useSWR from "swr";
import { ZodError } from "zod";

import { FormSelect } from "@/components/form-controls";
import { HoldToConfirmButton } from "@/components/hold-to-confirm-button";
import { LoadingIndicator } from "@/components/loading-indicator";
import { LocationSelect } from "@/components/order-management";
import { ApiError } from "@/src/api/api-fetch";
import { staffLocationMenuKey, staffLocationsKey } from "@/src/api/cache-keys";
import { listLocations } from "@/src/api/locations";
import {
  deleteLocationMenu,
  getLocationMenu,
  parseMenuItems,
  saveLocationMenu,
  type Menu,
} from "@/src/api/menus";
import { toastError } from "@/src/toast";

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Sprawdź pozycje menu.";
  }
  if (error instanceof ApiError) return error.message;

  return "Nie udało się wykonać operacji na menu. Sprawdź połączenie i spróbuj ponownie.";
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

export function itemCount(count: number): string {
  const lastDigit = count % 10;
  const lastTwoDigits = count % 100;
  const noun =
    count === 1
      ? "pozycja"
      : lastDigit >= 2 &&
          lastDigit <= 4 &&
          (lastTwoDigits < 12 || lastTwoDigits > 14)
        ? "pozycje"
        : "pozycji";

  return `${count.toLocaleString("pl-PL")} ${noun}`;
}

export function MenuManagement({
  accountId,
  onRequestedLocationApplied,
  requestedLocationId,
}: {
  accountId: string;
  onRequestedLocationApplied?: () => void;
  requestedLocationId?: string;
}) {
  const [selectedLocationId, setSelectedLocationId] = useState<string>();

  const {
    data: locations = [],
    error: locationsError,
    isLoading: areLocationsLoading,
  } = useSWR(staffLocationsKey(accountId), listLocations, {
    errorRetryCount: 3,
    shouldRetryOnError,
  });

  const effectiveLocationId = requestedLocationId ?? selectedLocationId;
  const location =
    locations.find((candidate) => candidate.id === effectiveLocationId) ??
    locations[0];

  const {
    data: menu,
    error: menuError,
    mutate: mutateMenu,
  } = useSWR(
    location ? staffLocationMenuKey(accountId, location.id) : null,
    ([, , , locationId]) => getLocationMenu(locationId),
    // A focus refresh with another editor's save would remount and drop the draft.
    { errorRetryCount: 3, revalidateOnFocus: false, shouldRetryOnError },
  );
  const loadError = locationsError ?? menuError;

  return (
    <div className="flex flex-col gap-6">
      <h1 className="sr-only">Menu</h1>

      {loadError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wczytać menu</Alert.Title>
            <Alert.Description>{getErrorMessage(loadError)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {location && locations.length > 1 && (
        <LocationSelect
          label="Lokal"
          locations={locations}
          selectedId={location.id}
          showAll={false}
          onChange={(locationId) => {
            setSelectedLocationId(locationId);
            onRequestedLocationApplied?.();
          }}
        />
      )}

      {areLocationsLoading || (location && menu === undefined && !menuError) ? (
        <LoadingIndicator label="Wczytywanie menu" />
      ) : (
        location &&
        menu !== undefined && (
          <MenuEditor
            // Remount on a new location or saved version so the draft starts from it.
            key={`${location.id}:${menu?.updatedAt ?? "none"}`}
            copySources={locations.filter(
              (candidate) => candidate.id !== location.id,
            )}
            location={location}
            menu={menu}
            onSaved={(saved) => mutateMenu(saved, { revalidate: false })}
          />
        )
      )}
    </div>
  );
}

function MenuEditor({
  copySources,
  location,
  menu,
  onSaved,
}: {
  copySources: Location[];
  location: Location;
  menu: Menu | null;
  onSaved: (menu: Menu | null) => Promise<unknown>;
}) {
  const savedItems = menu?.items.join("\n") ?? "";
  const [items, setItems] = useState(savedItems);
  const [error, setError] = useState<unknown>();
  const [pendingAction, setPendingAction] = useState<
    "save" | "delete" | "copy"
  >();

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pendingAction) return;

    setError(undefined);
    setPendingAction("save");
    try {
      await onSaved(await saveLocationMenu(location.id, parseMenuItems(items)));
      toast.success(`Zapisano menu lokalu „${location.name}”`);
    } catch (caught) {
      setError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function remove() {
    if (pendingAction) return;

    setError(undefined);
    setPendingAction("delete");
    try {
      await deleteLocationMenu(location.id);
      await onSaved(null);
      toast.success(`Usunięto menu lokalu „${location.name}”`);
    } catch (caught) {
      setError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function copyFrom(source: Location) {
    setError(undefined);
    setPendingAction("copy");
    try {
      const copied = await getLocationMenu(source.id);

      if (copied) setItems(copied.items.join("\n"));
      else
        toastError(
          "Brak menu do skopiowania",
          `Lokal „${source.name}” nie ma menu.`,
        );
    } catch (caught) {
      setError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  return (
    <form className="flex max-w-xl flex-col gap-4" onSubmit={save}>
      <div>
        <h2 className="section-title">
          {menu ? itemCount(menu.items.length) : "Brak menu"}
        </h2>
        {!menu && (
          <p className="mt-2 secondary-text">
            Dodaj pozycje, aby obsługa mogła je wybierać przy tworzeniu
            zamówienia.
          </p>
        )}
      </div>
      {error !== undefined && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się zapisać menu</Alert.Title>
            <Alert.Description>{getErrorMessage(error)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}
      {copySources.length > 0 && (
        <FormSelect
          className="w-full sm:max-w-xs"
          isDisabled={Boolean(pendingAction)}
          label="Kopiuj z lokalu"
          placeholder="Kopiuj z innego lokalu"
          selectedKey={null}
          onSelectionChange={(key) => {
            const source = copySources.find(
              (candidate) => candidate.id === key,
            );

            if (source) void copyFrom(source);
          }}
        >
          <ListBox items={copySources}>
            {(source) => (
              <ListBox.Item id={source.id} textValue={source.name}>
                {source.name}
              </ListBox.Item>
            )}
          </ListBox>
        </FormSelect>
      )}
      <TextField
        fullWidth
        isRequired
        isDisabled={Boolean(pendingAction)}
        name="menu-items"
        value={items}
        onChange={setItems}
      >
        <Label className="sr-only">Pozycje menu</Label>
        <TextArea
          placeholder="Pozycje menu, każda w osobnym wierszu"
          rows={12}
        />
      </TextField>
      <div className="flex flex-wrap justify-end gap-2">
        {menu && (
          <HoldToConfirmButton
            className="sm:mr-auto"
            isDisabled={Boolean(pendingAction)}
            isPending={pendingAction === "delete"}
            onConfirm={remove}
          >
            <DeleteIcon size={18} /> Przytrzymaj, aby usunąć
          </HoldToConfirmButton>
        )}
        <Button
          isDisabled={items === savedItems || pendingAction === "copy"}
          isPending={pendingAction === "save"}
          type="submit"
        >
          {pendingAction === "save" ? "Zapisywanie…" : "Zapisz"}
        </Button>
      </div>
    </form>
  );
}

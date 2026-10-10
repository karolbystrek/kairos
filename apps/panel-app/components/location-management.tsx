"use client";

import type { FormEvent } from "react";

import { Alert, Button, Spinner, Tooltip } from "@heroui/react";
import {
  ArrowUpRight,
  Ban as DisableIcon,
  Check as EnableIcon,
  Pencil as EditIcon,
  Plus as PlusIcon,
  Trash2 as DeleteIcon,
} from "lucide-react";
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { ZodError } from "zod";

import { HoldToConfirmButton } from "@/components/hold-to-confirm-button";
import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
import { LocationCreationModal } from "@/components/location-creation-modal";
import { PanelCard } from "@/components/panel-card";
import { PanelDetailHeader } from "@/components/panel-detail-header";
import { ApiError } from "@/src/api/api-fetch";
import { staffLocationsKey } from "@/src/api/cache-keys";
import {
  deleteLocation,
  listLocations,
  managedLocationNameSchema,
  renameLocation,
  sortLocations,
  updateLocationStatus,
  updateLocationReviewLink,
  type Location,
} from "@/src/api/locations";

const activeOrdersProblemType = "urn:kairos:problem:location-active-orders";

type Confirmation =
  | { kind: "disable"; location: Location }
  | { kind: "enable"; location: Location }
  | { kind: "delete"; location: Location };

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Podaj prawidłową nazwę lokalu.";
  }
  if (
    error instanceof ApiError &&
    error.problem?.type === "urn:kairos:problem:location-last-location"
  ) {
    return "Nie można usunąć ostatniego lokalu. Możesz go wyłączyć.";
  }
  if (error instanceof ApiError && error.status === 409) {
    return "Nie można wykonać tej operacji w obecnym stanie lokalu.";
  }
  if (error instanceof ApiError) return error.message;

  return "Nie udało się wykonać operacji na lokalu. Sprawdź połączenie i spróbuj ponownie.";
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

export function LocationManagement({
  accountId,
  onViewOrders,
}: {
  accountId: string;
  onViewOrders: (locationId: string) => void;
}) {
  const { mutate: mutateCache } = useSWRConfig();
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editName, setEditName] = useState<string>();
  const [editReviewUrl, setEditReviewUrl] = useState<string>();
  const [error, setError] = useState<unknown>();
  const [actionError, setActionError] = useState<unknown>();
  const [pendingAction, setPendingAction] = useState<string>();

  const {
    data: locations = [],
    error: locationsError,
    isLoading,
    mutate: mutateLocations,
  } = useSWR(staffLocationsKey(accountId), listLocations, {
    errorRetryCount: 3,
    shouldRetryOnError,
  });

  const selectedLocation = locations.find(
    (location) => location.id === selectedLocationId,
  );
  const hasActiveOrdersConflict =
    actionError instanceof ApiError &&
    actionError.problem?.type === activeOrdersProblemType;

  function selectLocation(locationId?: string) {
    setSelectedLocationId(locationId);
    setEditName(undefined);
    setEditReviewUrl(undefined);
    setError(undefined);
    setActionError(undefined);
  }

  async function updateCachedLocation(updated: Location) {
    await mutateLocations(
      (current) =>
        sortLocations(
          (current ?? [updated]).map((location) =>
            location.id === updated.id ? updated : location,
          ),
        ),
      { revalidate: false },
    );
  }

  async function revalidateCascadeState() {
    await mutateCache(
      (key) => Array.isArray(key) && key[0] === "staff" && key[1] === accountId,
    );
  }

  async function submitRename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedLocation || editName === undefined || pendingAction) return;

    setError(undefined);
    setPendingAction("rename");
    try {
      const name = managedLocationNameSchema.parse(editName);
      const updated = await renameLocation(selectedLocation.id, name);

      await updateCachedLocation(updated);
      setEditName(undefined);
      void mutateLocations();
    } catch (caught) {
      setError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function submitReviews(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedLocation || pendingAction) return;
    setError(undefined);
    setPendingAction("reviews");
    try {
      const updated = await updateLocationReviewLink(
        selectedLocation.id,
        editReviewUrl ?? selectedLocation.googleReviewUrl ?? "",
      );

      await updateCachedLocation(updated);
      setEditReviewUrl(undefined);
      void mutateLocations();
    } catch (caught) {
      setError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function confirmAction(confirmation: Confirmation) {
    if (pendingAction) return;

    const actionKey = `${confirmation.kind}-${confirmation.location.id}`;

    setActionError(undefined);
    setPendingAction(actionKey);
    try {
      if (confirmation.kind === "delete") {
        await deleteLocation(confirmation.location.id);
        await mutateLocations(
          (current) =>
            (current ?? []).filter(
              (location) => location.id !== confirmation.location.id,
            ),
          { revalidate: false },
        );
        selectLocation();
      } else {
        const updated = await updateLocationStatus(
          confirmation.location.id,
          confirmation.kind === "enable" ? "ENABLED" : "DISABLED",
        );

        await updateCachedLocation(updated);
      }
      await revalidateCascadeState();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="sr-only">Lokale</h1>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              aria-label="Nowy lokal"
              className="rounded-md"
              size="lg"
              onPress={() => setIsCreateOpen(true)}
            >
              <PlusIcon size={20} />
              Nowy lokal
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content>Nowy lokal</Tooltip.Content>
        </Tooltip>
      </div>

      {(locationsError || error) && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wykonać operacji na lokalu</Alert.Title>
            <Alert.Description>
              {getErrorMessage(error ?? locationsError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {isLoading ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Wczytywanie lokali" />
        </div>
      ) : locations.length === 0 ? (
        <div className="py-12">
          <h2 className="section-title">Brak lokali</h2>
          <p className="mt-2 max-w-sm secondary-text">
            Utwórz lokal, aby zarządzać zamówieniami i dostępem pracowników.
          </p>
        </div>
      ) : (
        <>
          <section aria-label="Lokale" className="entity-card-grid">
            {locations.map((location) => (
              <PanelCard
                key={location.id}
                accessibilityLabel={`Pokaż lokal ${location.name}, ${location.status === "ENABLED" ? "włączony" : "wyłączony"}`}
                isSelected={location.id === selectedLocation?.id}
                status={location.status}
                title={location.name}
                onPress={() => {
                  selectLocation(location.id);
                }}
              />
            ))}
          </section>

          {selectedLocation && (
            <PanelPopup
              key={selectedLocation.id}
              isOpen
              aria-label={`Lokal ${selectedLocation.name}`}
              onOpenChange={(open) => {
                if (!open) selectLocation();
              }}
            >
              <PanelPopup.Body className="pb-6 pt-12">
                <PanelDetailHeader
                  eyebrow="Lokal"
                  title={selectedLocation.name}
                  titleAction={
                    editName === undefined ? (
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label="Edytuj nazwę lokalu"
                            className="entity-name-edit shrink-0"
                            isDisabled={Boolean(pendingAction)}
                            variant="secondary"
                            onPress={() => setEditName(selectedLocation.name)}
                          >
                            <EditIcon size={18} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>Edytuj nazwę lokalu</Tooltip.Content>
                      </Tooltip>
                    ) : undefined
                  }
                  titleEditor={
                    editName === undefined ? undefined : (
                      <form
                        className="flex min-w-0 items-end gap-2"
                        onSubmit={submitRename}
                      >
                        <FormTextField
                          fullWidth
                          isRequired
                          isDisabled={pendingAction === "rename"}
                          label="Nazwa lokalu"
                          maxLength={120}
                          name="location-name"
                          value={editName}
                          onChange={(value) => {
                            setEditName(value);
                            setError(undefined);
                          }}
                        />
                        <Tooltip delay={500}>
                          <Tooltip.Trigger>
                            <Button
                              aria-label="Zapisz nazwę lokalu"
                              className="shrink-0 rounded-md"
                              isPending={pendingAction === "rename"}
                              type="submit"
                            >
                              <EnableIcon size={20} />
                              Zapisz
                            </Button>
                          </Tooltip.Trigger>
                          <Tooltip.Content>Zapisz</Tooltip.Content>
                        </Tooltip>
                        <Tooltip delay={500}>
                          <Tooltip.Trigger>
                            <Button
                              aria-label="Anuluj edycję nazwy lokalu"
                              className="shrink-0 rounded-md"
                              isDisabled={pendingAction === "rename"}
                              variant="tertiary"
                              onPress={() => {
                                setEditName(undefined);
                                setEditReviewUrl(undefined);
                                setError(undefined);
                              }}
                            >
                              Anuluj
                            </Button>
                          </Tooltip.Trigger>
                          <Tooltip.Content>Anuluj</Tooltip.Content>
                        </Tooltip>
                      </form>
                    )
                  }
                  trailingActions={
                    editName === undefined ? (
                      <>
                        {selectedLocation.status === "ENABLED" ? (
                          <HoldToConfirmButton
                            isDisabled={Boolean(pendingAction)}
                            isPending={
                              pendingAction === `disable-${selectedLocation.id}`
                            }
                            onConfirm={() =>
                              confirmAction({
                                kind: "disable",
                                location: selectedLocation,
                              })
                            }
                          >
                            <DisableIcon size={18} /> Przytrzymaj, aby wyłączyć
                          </HoldToConfirmButton>
                        ) : (
                          <Button
                            isDisabled={Boolean(pendingAction)}
                            isPending={
                              pendingAction === `enable-${selectedLocation.id}`
                            }
                            variant="secondary"
                            onPress={() =>
                              void confirmAction({
                                kind: "enable",
                                location: selectedLocation,
                              })
                            }
                          >
                            <EnableIcon size={18} /> Włącz
                          </Button>
                        )}
                        <HoldToConfirmButton
                          isDisabled={
                            Boolean(pendingAction) ||
                            locations.length <= 1 ||
                            selectedLocation.status !== "DISABLED"
                          }
                          isPending={
                            pendingAction === `delete-${selectedLocation.id}`
                          }
                          onConfirm={() =>
                            confirmAction({
                              kind: "delete",
                              location: selectedLocation,
                            })
                          }
                        >
                          <DeleteIcon size={18} /> Przytrzymaj, aby usunąć
                        </HoldToConfirmButton>
                      </>
                    ) : null
                  }
                />
                <p className="mt-4 text-sm text-muted">
                  Status:{" "}
                  {selectedLocation.status === "ENABLED"
                    ? "Włączony"
                    : "Wyłączony"}
                </p>
                <p className="mt-3 text-sm text-muted">
                  {selectedLocation.status === "ENABLED"
                    ? "Wyłączenie wyłączy i wyloguje przypisane konta oraz unieważni zaproszenia. Najpierw zakończ aktywne zamówienia."
                    : "Włączenie obejmie wszystkie przypisane konta, także wyłączone osobno. Usunięcie lokalu i kont jest nieodwracalne. Historia zamówień pozostanie dostępna."}
                </p>
                {actionError !== undefined && (
                  <Alert status="danger">
                    <Alert.Content>
                      <Alert.Title>
                        Nie udało się wykonać operacji na lokalu
                      </Alert.Title>
                      <Alert.Description>
                        {hasActiveOrdersConflict
                          ? "Najpierw zakończ lub anuluj aktywne zamówienia."
                          : getErrorMessage(actionError)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
                {hasActiveOrdersConflict && (
                  <Button
                    variant="secondary"
                    onPress={() => {
                      selectLocation();
                      onViewOrders(selectedLocation.id);
                    }}
                  >
                    <ArrowUpRight size={18} /> Pokaż zamówienia
                  </Button>
                )}
                {error !== undefined && (
                  <Alert status="danger">
                    <Alert.Content>
                      <Alert.Title>Nie udało się zapisać zmian</Alert.Title>
                      <Alert.Description>
                        {getErrorMessage(error)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
                <form
                  className="mt-6 flex flex-col gap-3"
                  onSubmit={submitReviews}
                >
                  <FormTextField
                    fullWidth
                    inputProps={{ type: "url" }}
                    isDisabled={Boolean(pendingAction)}
                    label="Link do opinii Google"
                    maxLength={2048}
                    name="google-review-url"
                    placeholder="Link do opinii Google (puste pole wyłącza zaproszenia)"
                    value={
                      editReviewUrl ?? selectedLocation.googleReviewUrl ?? ""
                    }
                    onChange={setEditReviewUrl}
                  />
                  <p className="secondary-text">
                    Zaproszenie do opinii wysyłamy raz, po odebraniu zamówienia.
                  </p>
                  <Button
                    className="self-start"
                    isDisabled={Boolean(pendingAction)}
                    isPending={pendingAction === "reviews"}
                    type="submit"
                  >
                    <EnableIcon size={18} /> Zapisz ustawienia opinii
                  </Button>
                </form>
                {locations.length <= 1 && (
                  <p className="mt-4 text-sm text-muted">
                    Ostatni lokal można wyłączyć, ale nie można go usunąć.
                  </p>
                )}
              </PanelPopup.Body>
            </PanelPopup>
          )}
        </>
      )}

      <LocationCreationModal
        accountId={accountId}
        isOpen={isCreateOpen}
        onCreated={(location) => selectLocation(location.id)}
        onOpenChange={setIsCreateOpen}
      />
    </div>
  );
}

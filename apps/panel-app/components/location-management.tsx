"use client";

import type { FormEvent } from "react";

import { Alert, Button, Tooltip, toast } from "@heroui/react";
import {
  ArrowUpRightIcon,
  ProhibitIcon as DisableIcon,
  CheckIcon as EnableIcon,
  PlusIcon,
  TrashIcon as DeleteIcon,
} from "@phosphor-icons/react";
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";

import { HoldToConfirmButton } from "@/components/hold-to-confirm-button";
import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
import { LocationCreationModal } from "@/components/location-creation-modal";
import { LoadingIndicator } from "@/components/loading-indicator";
import { PanelCard } from "@/components/panel-card";
import { PanelDetailHeader } from "@/components/panel-detail-header";
import { lifecycleStatus } from "@/components/status-chip";
import { ApiError } from "@/src/api/api-fetch";
import { itemCount } from "@/components/menu-management";
import { staffLocationMenuKey } from "@/src/api/cache-keys";
import {
  deleteLocation,
  googleReviewUrlSchema,
  managedLocationNameSchema,
  renameLocation,
  sortLocations,
  updateLocationStatus,
  updateLocationReviewLink,
  useStaffLocations,
  type Location,
} from "@/src/api/locations";
import { getLocationMenu } from "@/src/api/menus";
import { errorMessage } from "@/src/messages";

const activeOrdersProblemType = "urn:kairos:problem:location-active-orders";

type Confirmation =
  | { kind: "disable"; location: Location }
  | { kind: "enable"; location: Location }
  | { kind: "delete"; location: Location };

function getErrorMessage(error: unknown): string {
  if (
    error instanceof ApiError &&
    error.problem?.type === "urn:kairos:problem:location-last-location"
  ) {
    return "Nie można usunąć ostatniego lokalu. Możesz go wyłączyć.";
  }
  if (error instanceof ApiError && error.status === 409) {
    return "Nie można wykonać tej operacji w obecnym stanie lokalu.";
  }

  return errorMessage(
    error,
    "Nie udało się wykonać operacji na lokalu. Sprawdź połączenie i spróbuj ponownie.",
  );
}

function LocationMenuSummary({
  accountId,
  locationId,
  onEditMenu,
}: {
  accountId: string;
  locationId: string;
  onEditMenu: () => void;
}) {
  const { data: menu, error } = useSWR(
    staffLocationMenuKey(accountId, locationId),
    ([, , , id]) => getLocationMenu(id),
  );

  return (
    <div className="flex items-center justify-between gap-4">
      <div className="flex min-w-0 flex-col">
        <span className="text-sm secondary-text">Menu</span>
        {error ? (
          <span className="text-danger" role="alert">
            Nie udało się wczytać menu tego lokalu.
          </span>
        ) : menu === undefined ? (
          <LoadingIndicator label="Wczytywanie menu lokalu" />
        ) : (
          <span>{menu ? itemCount(menu.items.length) : "Brak menu"}</span>
        )}
      </div>
      <Button variant="secondary" onPress={onEditMenu}>
        <ArrowUpRightIcon size={18} /> Edytuj
      </Button>
    </div>
  );
}

export function LocationManagement({
  accountId,
  onEditMenu,
  onViewOrders,
}: {
  accountId: string;
  onEditMenu: (locationId: string) => void;
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
  } = useStaffLocations(accountId);

  const selectedLocation = locations.find(
    (location) => location.id === selectedLocationId,
  );
  const name = editName ?? selectedLocation?.name ?? "";
  const reviewUrl = editReviewUrl ?? selectedLocation?.googleReviewUrl ?? "";
  const isNameChanged = name.trim() !== selectedLocation?.name;
  const isReviewUrlChanged =
    reviewUrl.trim() !== (selectedLocation?.googleReviewUrl ?? "");
  const hasChanges = isNameChanged || isReviewUrlChanged;
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

  // Each changed field has its own endpoint; a failure keeps the draft so Save can retry the rest.
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedLocation || !hasChanges || pendingAction) return;

    setError(undefined);
    setPendingAction("save");
    try {
      // Validate every changed field before the first request.
      if (isNameChanged) managedLocationNameSchema.parse(name);
      if (isReviewUrlChanged) googleReviewUrlSchema.parse(reviewUrl);

      if (isNameChanged) {
        await updateCachedLocation(
          await renameLocation(selectedLocation.id, name),
        );
      }
      if (isReviewUrlChanged) {
        await updateCachedLocation(
          await updateLocationReviewLink(selectedLocation.id, reviewUrl),
        );
      }
      selectLocation();
      toast.success("Zapisano zmiany lokalu");
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
        toast.success(`Usunięto lokal „${confirmation.location.name}”`);
      } else {
        const updated = await updateLocationStatus(
          confirmation.location.id,
          confirmation.kind === "enable" ? "ENABLED" : "DISABLED",
        );

        await updateCachedLocation(updated);
        toast.success(
          `${updated.status === "ENABLED" ? "Włączono" : "Wyłączono"} lokal „${updated.name}”`,
        );
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

      {locationsError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wczytać lokali</Alert.Title>
            <Alert.Description>
              {getErrorMessage(locationsError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {isLoading ? (
        <LoadingIndicator label="Wczytywanie lokali" />
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
                status={lifecycleStatus(location.status, "y")}
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
              size="lg"
              onOpenChange={(open) => {
                if (!open) selectLocation();
              }}
            >
              <PanelDetailHeader
                status={lifecycleStatus(selectedLocation.status, "y")}
                title={selectedLocation.name}
              />
              <form onSubmit={save}>
                <PanelPopup.Body className="flex flex-col gap-5">
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
                  <FormTextField
                    fullWidth
                    isRequired
                    isDisabled={Boolean(pendingAction)}
                    label="Nazwa lokalu"
                    maxLength={120}
                    name="location-name"
                    value={name}
                    onChange={setEditName}
                  />
                  <LocationMenuSummary
                    accountId={accountId}
                    locationId={selectedLocation.id}
                    onEditMenu={() => {
                      selectLocation();
                      onEditMenu(selectedLocation.id);
                    }}
                  />
                  <div className="flex flex-col gap-2">
                    <FormTextField
                      fullWidth
                      inputProps={{ type: "url" }}
                      isDisabled={Boolean(pendingAction)}
                      label="Link do opinii Google"
                      maxLength={2048}
                      name="google-review-url"
                      value={reviewUrl}
                      onChange={setEditReviewUrl}
                    />
                    <p className="secondary-text">
                      Klient zobaczy prośbę o opinię po odebraniu zamówienia.
                      Puste pole wyłącza prośby.
                    </p>
                  </div>
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
                      className="self-start"
                      variant="secondary"
                      onPress={() => {
                        selectLocation();
                        onViewOrders(selectedLocation.id);
                      }}
                    >
                      <ArrowUpRightIcon size={18} /> Pokaż zamówienia
                    </Button>
                  )}
                  <p className="text-sm text-muted">
                    {selectedLocation.status === "ENABLED"
                      ? "Wyłączenie wyloguje przypisane konta i unieważni zaproszenia."
                      : locations.length <= 1
                        ? "Ostatni lokal można wyłączyć, ale nie można go usunąć."
                        : "Usunięcia lokalu i jego kont nie można cofnąć."}
                  </p>
                </PanelPopup.Body>
                <PanelPopup.Footer className="popup-action-grid">
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
                  <Button
                    isDisabled={Boolean(pendingAction)}
                    slot="close"
                    variant="tertiary"
                  >
                    Anuluj
                  </Button>
                  <Button
                    isDisabled={!hasChanges}
                    isPending={pendingAction === "save"}
                    type="submit"
                  >
                    {pendingAction === "save" ? "Zapisywanie…" : "Zapisz"}
                  </Button>
                </PanelPopup.Footer>
              </form>
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

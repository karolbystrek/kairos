"use client";

import type { FormEvent } from "react";

import { Alert, Button, Spinner, Tooltip } from "@heroui/react";
import {
  ArrowRight as ArrowRightIcon,
  Ban as DisableIcon,
  Check as EnableIcon,
  Pencil as EditIcon,
  Plus as PlusIcon,
  Trash2 as DeleteIcon,
  X as CancelIcon,
} from "lucide-react";
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { ZodError } from "zod";

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
  const [confirmation, setConfirmation] = useState<Confirmation>();
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
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

  const selectedLocation =
    locations.find((location) => location.id === selectedLocationId) ??
    locations[0];
  const hasActiveOrdersConflict =
    actionError instanceof ApiError &&
    actionError.problem?.type === activeOrdersProblemType;

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

  async function confirmAction() {
    if (!confirmation || pendingAction) return;

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
        setSelectedLocationId(undefined);
      } else {
        const updated = await updateLocationStatus(
          confirmation.location.id,
          confirmation.kind === "enable" ? "ENABLED" : "DISABLED",
        );

        await updateCachedLocation(updated);
      }
      setConfirmation(undefined);
      setDeleteConfirmation("");
      await revalidateCascadeState();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  function openConfirmation(next: Confirmation) {
    if (pendingAction) return;
    setActionError(undefined);
    setDeleteConfirmation("");
    setConfirmation(next);
  }

  const confirmationTitle = confirmation
    ? confirmation.kind === "disable"
      ? `Wyłączyć ${confirmation.location.name}?`
      : confirmation.kind === "enable"
        ? `Włączyć ${confirmation.location.name}?`
        : `Usunąć ${confirmation.location.name}?`
    : "Potwierdź operację na lokalu";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="page-title">Lokale</h1>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label="Nowy lokal"
              className="rounded-md"
              size="lg"
              onPress={() => setIsCreateOpen(true)}
            >
              <PlusIcon size={20} />
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
        <div className="grid gap-6 md:grid-cols-[minmax(220px,0.65fr)_minmax(0,1.35fr)]">
          <section className="border-t border-separator pt-2 md:border-r md:border-t-0 md:pr-6 md:pt-0">
            {locations.map((location) => (
              <PanelCard
                key={location.id}
                accessibilityLabel={`Pokaż lokal ${location.name}`}
                isSelected={location.id === selectedLocation?.id}
                metadata={
                  <span
                    className={
                      location.status === "ENABLED"
                        ? "text-accent"
                        : "secondary-text"
                    }
                  >
                    {location.status === "ENABLED" ? "Włączony" : "Wyłączony"}
                  </span>
                }
                title={location.name}
                trailing={<ArrowRightIcon size={17} />}
                onPress={() => {
                  setSelectedLocationId(location.id);
                  setEditName(undefined);
                  setEditReviewUrl(undefined);
                  setError(undefined);
                }}
              />
            ))}
          </section>

          {selectedLocation && (
            <section className="min-w-0">
              <PanelDetailHeader
                eyebrow="Lokal"
                title={selectedLocation.name}
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
                            isIconOnly
                            aria-label="Zapisz nazwę lokalu"
                            className="shrink-0 rounded-md"
                            isPending={pendingAction === "rename"}
                            type="submit"
                          >
                            <EnableIcon size={20} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>Zapisz</Tooltip.Content>
                      </Tooltip>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
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
                            <CancelIcon size={20} />
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
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label="Edytuj nazwę lokalu"
                            className="rounded-md"
                            onPress={() => setEditName(selectedLocation.name)}
                          >
                            <EditIcon size={20} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>Edytuj</Tooltip.Content>
                      </Tooltip>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label={`${selectedLocation.status === "ENABLED" ? "Wyłącz" : "Włącz"} lokal`}
                            className="rounded-md"
                            variant={
                              selectedLocation.status === "ENABLED"
                                ? "danger"
                                : "secondary"
                            }
                            onPress={() =>
                              openConfirmation({
                                kind:
                                  selectedLocation.status === "ENABLED"
                                    ? "disable"
                                    : "enable",
                                location: selectedLocation,
                              })
                            }
                          >
                            {selectedLocation.status === "ENABLED" ? (
                              <DisableIcon size={20} />
                            ) : (
                              <EnableIcon size={20} />
                            )}
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                          {selectedLocation.status === "ENABLED"
                            ? "Wyłącz"
                            : "Włącz"}
                        </Tooltip.Content>
                      </Tooltip>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label="Usuń lokal"
                            className="rounded-md"
                            isDisabled={
                              locations.length <= 1 ||
                              selectedLocation.status !== "DISABLED"
                            }
                            variant="danger"
                            onPress={() =>
                              openConfirmation({
                                kind: "delete",
                                location: selectedLocation,
                              })
                            }
                          >
                            <DeleteIcon size={20} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                          {locations.length <= 1
                            ? "Nie można usunąć ostatniego lokalu"
                            : selectedLocation.status === "DISABLED"
                              ? "Usuń"
                              : "Wyłącz przed usunięciem"}
                        </Tooltip.Content>
                      </Tooltip>
                    </>
                  ) : null
                }
              />
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
                  Klienci otrzymują jedno zaproszenie po odebraniu zamówienia.
                </p>
                <Button
                  className="self-start"
                  isDisabled={Boolean(pendingAction)}
                  isPending={pendingAction === "reviews"}
                  type="submit"
                >
                  Zapisz ustawienia opinii
                </Button>
              </form>
              {locations.length <= 1 && (
                <p className="mt-4 text-sm text-muted">
                  Ostatni lokal można wyłączyć, ale nie można go usunąć.
                </p>
              )}
            </section>
          )}
        </div>
      )}

      <LocationCreationModal
        accountId={accountId}
        isOpen={isCreateOpen}
        onCreated={(location) => setSelectedLocationId(location.id)}
        onOpenChange={setIsCreateOpen}
      />

      <PanelPopup
        className="sm:max-w-[480px]"
        isOpen={Boolean(confirmation)}
        role="alertdialog"
        onOpenChange={(open) => {
          if (!open) {
            setConfirmation(undefined);
            setActionError(undefined);
            setDeleteConfirmation("");
          }
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Icon
            status={confirmation?.kind === "enable" ? "warning" : "danger"}
          />
          <PanelPopup.Heading>{confirmationTitle}</PanelPopup.Heading>
        </PanelPopup.Header>
        <PanelPopup.Body className="flex flex-col gap-4">
          {confirmation?.kind === "disable" && (
            <p>
              Przypisane konta zostaną wyłączone i wylogowane. Oczekujące
              zaproszenia do tego lokalu zostaną unieważnione.
            </p>
          )}
          {confirmation?.kind === "enable" && (
            <p>
              Wszystkie przypisane konta, które nie są zarchiwizowane, zostaną
              włączone, również te wyłączone osobno.
            </p>
          )}
          {confirmation?.kind === "delete" && (
            <>
              <p>
                Lokal i przypisane konta znikną z panelu zarządzania i nie
                będzie można ich przywrócić. Historia zamówień pozostanie
                dostępna. Aby potwierdzić, wpisz dokładną nazwę lokalu.
              </p>
              <FormTextField
                fullWidth
                isRequired
                inputProps={{ autoComplete: "off" }}
                isDisabled={Boolean(pendingAction)}
                label={`Wpisz ${confirmation.location.name} w celu potwierdzenia`}
                name="delete-location-confirmation"
                value={deleteConfirmation}
                onChange={setDeleteConfirmation}
              />
            </>
          )}
          {actionError !== undefined && (
            <Alert status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>
                  {hasActiveOrdersConflict
                    ? "Najpierw zakończ aktywne zamówienia"
                    : "Nie udało się wykonać operacji na lokalu"}
                </Alert.Title>
                <Alert.Description>
                  {hasActiveOrdersConflict
                    ? "Przed wyłączeniem lokalu zakończ lub anuluj wszystkie aktywne zamówienia."
                    : getErrorMessage(actionError)}
                </Alert.Description>
              </Alert.Content>
            </Alert>
          )}
        </PanelPopup.Body>
        <PanelPopup.Footer>
          <Button slot="close" variant="tertiary">
            Anuluj
          </Button>
          {hasActiveOrdersConflict && confirmation && (
            <Button
              variant="secondary"
              onPress={() => {
                const locationId = confirmation.location.id;

                setConfirmation(undefined);
                setActionError(undefined);
                onViewOrders(locationId);
              }}
            >
              Pokaż zamówienia
            </Button>
          )}
          <Button
            isDisabled={
              confirmation?.kind === "delete" &&
              deleteConfirmation !== confirmation.location.name
            }
            isPending={Boolean(pendingAction)}
            variant={confirmation?.kind === "enable" ? "primary" : "danger"}
            onPress={() => void confirmAction()}
          >
            {confirmation?.kind === "disable"
              ? "Wyłącz"
              : confirmation?.kind === "enable"
                ? "Włącz"
                : "Usuń"}
          </Button>
        </PanelPopup.Footer>
      </PanelPopup>
    </div>
  );
}

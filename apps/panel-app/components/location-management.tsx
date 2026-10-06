"use client";

import type { FormEvent } from "react";

import { Alert, AlertDialog, Button, Spinner, Tooltip } from "@heroui/react";
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
  type Location,
} from "@/src/api/locations";

const activeOrdersProblemType = "urn:kairos:problem:location-active-orders";

type Confirmation =
  | { kind: "disable"; location: Location }
  | { kind: "enable"; location: Location }
  | { kind: "delete"; location: Location };

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Enter a valid location name.";
  }
  if (error instanceof ApiError && error.status === 409) {
    return "This action conflicts with the current location state.";
  }
  if (error instanceof ApiError) return error.message;

  return "Location management could not be completed. Check your connection and try again.";
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
    setActionError(undefined);
    setDeleteConfirmation("");
    setConfirmation(next);
  }

  const confirmationTitle = confirmation
    ? confirmation.kind === "disable"
      ? `Disable ${confirmation.location.name}?`
      : confirmation.kind === "enable"
        ? `Enable ${confirmation.location.name}?`
        : `Delete ${confirmation.location.name}?`
    : "Confirm location action";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="page-title">Locations</h1>
        <Tooltip delay={500}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              aria-label="New location"
              className="rounded-md"
              size="lg"
              onPress={() => setIsCreateOpen(true)}
            >
              <PlusIcon size={20} />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content>New location</Tooltip.Content>
        </Tooltip>
      </div>

      {(locationsError || error) && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Location request failed</Alert.Title>
            <Alert.Description>
              {getErrorMessage(error ?? locationsError)}
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {isLoading ? (
        <div className="flex min-h-80 items-center justify-center">
          <Spinner aria-label="Loading locations" />
        </div>
      ) : locations.length === 0 ? (
        <div className="py-12">
          <h2 className="section-title">No locations yet</h2>
          <p className="mt-2 max-w-sm secondary-text">
            Create a location to start managing orders and staff access.
          </p>
        </div>
      ) : (
        <div className="grid gap-6 md:grid-cols-[minmax(220px,0.65fr)_minmax(0,1.35fr)]">
          <section className="border-t border-separator pt-2 md:border-r md:border-t-0 md:pr-6 md:pt-0">
            {locations.map((location) => (
              <PanelCard
                key={location.id}
                accessibilityLabel={`View location ${location.name}`}
                isSelected={location.id === selectedLocation?.id}
                metadata={
                  <span
                    className={
                      location.status === "ENABLED"
                        ? "text-accent"
                        : "secondary-text"
                    }
                  >
                    {location.status === "ENABLED" ? "Enabled" : "Disabled"}
                  </span>
                }
                title={location.name}
                trailing={<ArrowRightIcon size={17} />}
                onPress={() => {
                  setSelectedLocationId(location.id);
                  setEditName(undefined);
                  setError(undefined);
                }}
              />
            ))}
          </section>

          {selectedLocation && (
            <section className="min-w-0">
              <PanelDetailHeader
                eyebrow="Location"
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
                        label="Location name"
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
                            aria-label="Save location name"
                            className="shrink-0 rounded-md"
                            isPending={pendingAction === "rename"}
                            type="submit"
                          >
                            <EnableIcon size={20} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>Save</Tooltip.Content>
                      </Tooltip>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label="Cancel editing location name"
                            className="shrink-0 rounded-md"
                            isDisabled={pendingAction === "rename"}
                            variant="tertiary"
                            onPress={() => {
                              setEditName(undefined);
                              setError(undefined);
                            }}
                          >
                            <CancelIcon size={20} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>Cancel</Tooltip.Content>
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
                            aria-label="Edit location name"
                            className="rounded-md"
                            onPress={() => setEditName(selectedLocation.name)}
                          >
                            <EditIcon size={20} />
                          </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>Edit</Tooltip.Content>
                      </Tooltip>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label={`${selectedLocation.status === "ENABLED" ? "Disable" : "Enable"} location`}
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
                            ? "Disable"
                            : "Enable"}
                        </Tooltip.Content>
                      </Tooltip>
                      <Tooltip delay={500}>
                        <Tooltip.Trigger>
                          <Button
                            isIconOnly
                            aria-label="Delete location"
                            className="rounded-md"
                            isDisabled={selectedLocation.status !== "DISABLED"}
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
                          {selectedLocation.status === "DISABLED"
                            ? "Delete"
                            : "Disable before deleting"}
                        </Tooltip.Content>
                      </Tooltip>
                    </>
                  ) : null
                }
              />
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

      <AlertDialog
        isOpen={Boolean(confirmation)}
        onOpenChange={(open) => {
          if (!open && !pendingAction) {
            setConfirmation(undefined);
            setActionError(undefined);
            setDeleteConfirmation("");
          }
        }}
      >
        <AlertDialog.Backdrop>
          <AlertDialog.Container>
            <AlertDialog.Dialog className="sm:max-w-[480px]">
              <AlertDialog.CloseTrigger />
              <AlertDialog.Header>
                <AlertDialog.Icon
                  status={
                    confirmation?.kind === "enable" ? "warning" : "danger"
                  }
                />
                <AlertDialog.Heading>{confirmationTitle}</AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body className="flex flex-col gap-4">
                {confirmation?.kind === "disable" && (
                  <p>
                    Assigned accounts will be disabled and signed out. Pending
                    invitations for this location will be revoked.
                  </p>
                )}
                {confirmation?.kind === "enable" && (
                  <p>
                    Every assigned non-archived account will be enabled, even if
                    it was disabled independently.
                  </p>
                )}
                {confirmation?.kind === "delete" && (
                  <>
                    <p>
                      The location and its assigned accounts will disappear from
                      ordinary management and cannot be restored. Historical
                      orders remain readable. Type its exact name to confirm.
                    </p>
                    <FormTextField
                      fullWidth
                      isRequired
                      inputProps={{ autoComplete: "off" }}
                      isDisabled={Boolean(pendingAction)}
                      label={`Type ${confirmation.location.name} to confirm`}
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
                          ? "Active orders must be resolved"
                          : "Location action failed"}
                      </Alert.Title>
                      <Alert.Description>
                        {hasActiveOrdersConflict
                          ? "Complete or cancel every active order before disabling this location."
                          : getErrorMessage(actionError)}
                      </Alert.Description>
                    </Alert.Content>
                  </Alert>
                )}
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button
                  isDisabled={Boolean(pendingAction)}
                  slot="close"
                  variant="tertiary"
                >
                  Cancel
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
                    View orders
                  </Button>
                )}
                <Button
                  isDisabled={
                    confirmation?.kind === "delete" &&
                    deleteConfirmation !== confirmation.location.name
                  }
                  isPending={Boolean(pendingAction)}
                  variant={
                    confirmation?.kind === "enable" ? "primary" : "danger"
                  }
                  onPress={() => void confirmAction()}
                >
                  {confirmation?.kind === "disable"
                    ? "Disable"
                    : confirmation?.kind === "enable"
                      ? "Enable"
                      : "Delete"}
                </Button>
              </AlertDialog.Footer>
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
    </div>
  );
}

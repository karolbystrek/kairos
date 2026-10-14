"use client";

import type { FormEvent } from "react";

import { Alert, Button, toast } from "@heroui/react";
import { ArrowLeftIcon, ArrowRightIcon, PlusIcon } from "@phosphor-icons/react";
import { useRef, useState } from "react";
import { useSWRConfig } from "swr";

import { PanelPopup } from "@/components/panel-popup";
import { FormTextField } from "@/components/form-controls";
import { StepProgress } from "@/components/step-progress";
import { ApiError } from "@/src/api/api-fetch";
import { staffLocationsKey } from "@/src/api/cache-keys";
import {
  createLocation,
  googleReviewUrlSchema,
  managedLocationNameSchema,
  sortLocations,
  type Location,
} from "@/src/api/locations";

const steps = ["name", "review"] as const;

function getErrorMessage(error: unknown): string {
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
  const [stepIndex, setStepIndex] = useState(0);
  const [direction, setDirection] = useState<"forward" | "back">("forward");
  const stepChangedAt = useRef(0);
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState<string>();
  const [reviewUrl, setReviewUrl] = useState("");
  const [reviewUrlError, setReviewUrlError] = useState<string>();
  const [error, setError] = useState<unknown>();
  const [isCreating, setIsCreating] = useState(false);
  const step = steps[stepIndex];

  function reset() {
    setStepIndex(0);
    setDirection("forward");
    setName("");
    setNameError(undefined);
    setReviewUrl("");
    setReviewUrlError(undefined);
    setError(undefined);
  }

  function changeOpen(open: boolean) {
    onOpenChange(open);
    if (!open) reset();
  }

  // Proceed and Create share a position, so the second tap of a double-tap must not create.
  function goToStep(index: number) {
    stepChangedAt.current = performance.now();
    setDirection(index > stepIndex ? "forward" : "back");
    setStepIndex(index);
    setError(undefined);
  }

  function validateReviewUrl(): boolean {
    const result = googleReviewUrlSchema.safeParse(reviewUrl);

    setReviewUrlError(result.error?.issues[0]?.message);

    return result.success;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isCreating) return;

    const validatedName = managedLocationNameSchema.safeParse(name);

    if (!validatedName.success) {
      setNameError(validatedName.error.issues[0]?.message);
      goToStep(0);

      return;
    }
    if (step === "name") {
      goToStep(1);

      return;
    }
    if (performance.now() - stepChangedAt.current < 400) return;
    if (!validateReviewUrl()) return;

    setError(undefined);
    setIsCreating(true);
    try {
      const location = await createLocation(validatedName.data, reviewUrl);

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
      toast.success(`Utworzono lokal „${location.name}”`);
      onOpenChange(false);
      reset();
      void mutate(staffLocationsKey(accountId));
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        setNameError("Lokal o tej nazwie już istnieje.");
        goToStep(0);
      } else {
        setError(caught);
      }
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <PanelPopup
      isOpen={isOpen}
      isRequired={isRequired}
      size="md"
      onOpenChange={changeOpen}
    >
      <PanelPopup.Header className="gap-4">
        <StepProgress step={stepIndex + 1} total={steps.length} />
        <PanelPopup.Heading>
          {step === "review"
            ? "Opinie Google"
            : isRequired
              ? "Utwórz pierwszy lokal"
              : "Nazwa lokalu"}
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
          <div
            key={step}
            className="location-create-step flex flex-col gap-3"
            data-direction={direction}
          >
            {step === "name" ? (
              <FormTextField
                fullWidth
                isRequired
                errorMessage={nameError}
                inputProps={{ autoComplete: "organization", autoFocus: true }}
                isDisabled={isCreating}
                label="Nazwa lokalu"
                maxLength={120}
                name="location-name"
                value={name}
                onChange={(value) => {
                  setName(value);
                  setNameError(undefined);
                }}
              />
            ) : (
              <>
                <p className="secondary-text">
                  Po odebraniu zamówienia klient zobaczy prośbę o opinię. Link
                  skopiujesz w Profilu Firmy w Google, w opcji „Poproś o
                  opinie”. Możesz dodać go później.
                </p>
                <FormTextField
                  fullWidth
                  errorMessage={reviewUrlError}
                  inputProps={{ type: "url", autoFocus: true }}
                  isDisabled={isCreating}
                  label="Link do opinii Google (opcjonalnie)"
                  maxLength={2048}
                  name="google-review-url"
                  placeholder="https://g.page/r/…/review"
                  value={reviewUrl}
                  onBlur={validateReviewUrl}
                  onChange={(value) => {
                    setReviewUrl(value);
                    setReviewUrlError(undefined);
                  }}
                />
              </>
            )}
          </div>
        </PanelPopup.Body>
        <PanelPopup.Footer>
          {step === "review" ? (
            <Button
              isDisabled={isCreating}
              variant="tertiary"
              onPress={() => goToStep(0)}
            >
              <ArrowLeftIcon aria-hidden="true" size={18} />
              Wstecz
            </Button>
          ) : (
            !isRequired && (
              <Button aria-label="Anuluj" slot="close" variant="tertiary">
                Anuluj
              </Button>
            )
          )}
          {/* One button keeps Proceed and Create in the same place. */}
          <Button isPending={isCreating} type="submit">
            {step === "name" ? (
              <>
                Dalej
                <ArrowRightIcon aria-hidden="true" size={18} />
              </>
            ) : (
              <>
                <PlusIcon aria-hidden="true" size={18} />
                {isCreating ? "Tworzenie…" : "Utwórz lokal"}
              </>
            )}
          </Button>
        </PanelPopup.Footer>
      </form>
    </PanelPopup>
  );
}

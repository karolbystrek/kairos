"use client";

import type { FormEvent } from "react";

import {
  Alert,
  Button,
  Label,
  TextArea,
  TextField,
  Tooltip,
  toast,
} from "@heroui/react";
import { PlusIcon, TrashIcon as DeleteIcon } from "@phosphor-icons/react";
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { ZodError } from "zod";

import { FormTextField } from "@/components/form-controls";
import { HoldToConfirmButton } from "@/components/hold-to-confirm-button";
import { LoadingIndicator } from "@/components/loading-indicator";
import { PanelCard } from "@/components/panel-card";
import { PanelPopup } from "@/components/panel-popup";
import { ApiError } from "@/src/api/api-fetch";
import { staffMenusKey } from "@/src/api/cache-keys";
import {
  createMenu,
  deleteMenu,
  listMenus,
  parseMenuItems,
  updateMenu,
  type Menu,
} from "@/src/api/menus";

type MenuDraft = {
  menu?: Menu;
  name: string;
  items: string;
};

function getErrorMessage(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues[0]?.message ?? "Sprawdź nazwę i pozycje menu.";
  }
  if (error instanceof ApiError) {
    switch (error.problem?.type) {
      case "urn:kairos:problem:menu-name-conflict":
        return "Menu o tej nazwie już istnieje.";
      case "urn:kairos:problem:menu-in-use":
        return "Tego menu używa co najmniej jeden lokal. Wybierz w szczegółach tych lokali inne menu, zanim je usuniesz.";
    }

    return error.message;
  }

  return "Nie udało się wykonać operacji na menu. Sprawdź połączenie i spróbuj ponownie.";
}

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

function itemCount(count: number): string {
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
  canManageMenus,
}: {
  accountId: string;
  canManageMenus: boolean;
}) {
  const { mutate: mutateCache } = useSWRConfig();
  const [draft, setDraft] = useState<MenuDraft>();
  const [draftError, setDraftError] = useState<unknown>();
  const [pendingAction, setPendingAction] = useState<"save" | "delete">();

  const {
    data: menus = [],
    error: menusError,
    isLoading: areMenusLoading,
    mutate: mutateMenus,
  } = useSWR(staffMenusKey(accountId), listMenus, {
    errorRetryCount: 3,
    shouldRetryOnError,
  });

  function openDraft(menu?: Menu) {
    setDraftError(undefined);
    setDraft({
      menu,
      name: menu?.name ?? "",
      items: menu?.items.join("\n") ?? "",
    });
  }

  async function revalidateLocationMenus() {
    await mutateCache(
      (key) =>
        Array.isArray(key) &&
        key[0] === "staff" &&
        key[1] === accountId &&
        key[2] === "location-menu",
    );
  }

  async function saveDraft(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft || pendingAction) return;

    setDraftError(undefined);
    setPendingAction("save");
    try {
      const items = parseMenuItems(draft.items);
      const saved = draft.menu
        ? await updateMenu(draft.menu.id, draft.name, items)
        : await createMenu(draft.name, items);

      await mutateMenus(
        (current) =>
          [
            ...(current ?? []).filter((menu) => menu.id !== saved.id),
            saved,
          ].sort((first, second) =>
            first.name.localeCompare(second.name, "pl-PL", {
              sensitivity: "base",
            }),
          ),
        { revalidate: false },
      );
      setDraft(undefined);
      toast.success(`Zapisano menu „${saved.name}”`);
      void mutateMenus();
      if (draft.menu) await revalidateLocationMenus();
    } catch (caught) {
      setDraftError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  async function deleteDraftMenu() {
    if (!draft?.menu || pendingAction) return;

    const menuId = draft.menu.id;

    setDraftError(undefined);
    setPendingAction("delete");
    try {
      await deleteMenu(menuId);
      await mutateMenus(
        (current) => (current ?? []).filter((menu) => menu.id !== menuId),
        { revalidate: false },
      );
      setDraft(undefined);
      toast.success(`Usunięto menu „${draft.menu.name}”`);
    } catch (caught) {
      setDraftError(caught);
    } finally {
      setPendingAction(undefined);
    }
  }

  const loadError = menusError;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-4">
        <h1 className="sr-only">Menu</h1>
        {canManageMenus && (
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                aria-label="Nowe menu"
                className="rounded-md"
                size="lg"
                onPress={() => openDraft()}
              >
                <PlusIcon size={20} />
                Nowe menu
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>Nowe menu</Tooltip.Content>
          </Tooltip>
        )}
      </div>

      {loadError && (
        <Alert status="danger">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Nie udało się wczytać menu</Alert.Title>
            <Alert.Description>{getErrorMessage(loadError)}</Alert.Description>
          </Alert.Content>
        </Alert>
      )}

      {areMenusLoading ? (
        <LoadingIndicator label="Wczytywanie menu" />
      ) : (
        <>
          {menus.length === 0 ? (
            <div className="py-12">
              <h2 className="section-title">Brak menu</h2>
              <p className="mt-2 max-w-sm secondary-text">
                {canManageMenus
                  ? "Utwórz menu, aby obsługa mogła wybierać pozycje przy tworzeniu zamówienia."
                  : "Administrator nie utworzył jeszcze żadnego menu."}
              </p>
            </div>
          ) : (
            <section aria-label="Menu" className="entity-card-grid">
              {menus.map((menu) => (
                <PanelCard
                  key={menu.id}
                  accessibilityLabel={
                    canManageMenus ? `Edytuj menu ${menu.name}` : undefined
                  }
                  metadata={
                    <span className="secondary-text">
                      {canManageMenus
                        ? itemCount(menu.items.length)
                        : menu.items.join(", ")}
                    </span>
                  }
                  title={menu.name}
                  onPress={canManageMenus ? () => openDraft(menu) : undefined}
                />
              ))}
            </section>
          )}
        </>
      )}

      <PanelPopup
        isOpen={Boolean(draft)}
        size="md"
        onOpenChange={(open) => {
          if (!open && !pendingAction) setDraft(undefined);
        }}
      >
        <PanelPopup.Header>
          <PanelPopup.Heading>
            {draft?.menu ? "Edytuj menu" : "Nowe menu"}
          </PanelPopup.Heading>
        </PanelPopup.Header>
        <form onSubmit={saveDraft}>
          <PanelPopup.Body className="flex flex-col gap-4">
            {draftError !== undefined && (
              <Alert status="danger">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title>Nie udało się zapisać menu</Alert.Title>
                  <Alert.Description>
                    {getErrorMessage(draftError)}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )}
            <FormTextField
              fullWidth
              isRequired
              isDisabled={Boolean(pendingAction)}
              label="Nazwa menu"
              maxLength={120}
              name="menu-name"
              value={draft?.name ?? ""}
              onChange={(name) =>
                setDraft((current) => current && { ...current, name })
              }
            />
            <TextField
              fullWidth
              isRequired
              isDisabled={Boolean(pendingAction)}
              name="menu-items"
              value={draft?.items ?? ""}
              onChange={(items) =>
                setDraft((current) => current && { ...current, items })
              }
            >
              <Label className="sr-only">Pozycje menu</Label>
              <TextArea
                placeholder="Pozycje menu, każda w osobnym wierszu"
                rows={8}
              />
            </TextField>
            {draft?.menu && (
              <p className="secondary-text">
                Zmiany od razu obejmą wszystkie lokale korzystające z tego menu.
                Etykiety istniejących zamówień się nie zmienią.
              </p>
            )}
          </PanelPopup.Body>
          <PanelPopup.Footer className="flex-wrap">
            {draft?.menu && (
              <HoldToConfirmButton
                className="sm:mr-auto"
                isDisabled={Boolean(pendingAction)}
                isPending={pendingAction === "delete"}
                onConfirm={deleteDraftMenu}
              >
                <DeleteIcon size={18} /> Przytrzymaj, aby usunąć
              </HoldToConfirmButton>
            )}
            <Button
              aria-label="Anuluj"
              isDisabled={Boolean(pendingAction)}
              slot="close"
              variant="tertiary"
            >
              Anuluj
            </Button>
            <Button isPending={pendingAction === "save"} type="submit">
              {pendingAction === "save" ? "Zapisywanie…" : "Zapisz"}
            </Button>
          </PanelPopup.Footer>
        </form>
      </PanelPopup>
    </div>
  );
}

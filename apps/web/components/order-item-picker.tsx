import type { Menu } from "@/src/api/menus";

import { Button } from "@heroui/react";
import { MinusIcon } from "@phosphor-icons/react";
import { useState } from "react";

import { FormTextField } from "@/components/form-controls";

export type ItemQuantities = Record<string, number>;

const maximumQuantity = 99;
const searchThreshold = 12;

// Case- and diacritic-insensitive; "ł" has no Unicode decomposition.
function searchable(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase("pl-PL")
    .replaceAll("ł", "l");
}

export function OrderItemPicker({
  isDisabled,
  menu,
  onChange,
  quantities,
}: {
  isDisabled: boolean;
  menu: Menu;
  onChange: (quantities: ItemQuantities) => void;
  quantities: ItemQuantities;
}) {
  const [query, setQuery] = useState("");
  // Touch keyboards would cover the tiles, so focus search only with a precise pointer.
  const [hasFinePointer] = useState(
    () => window.matchMedia("(pointer: fine)").matches,
  );
  const needle = searchable(query.trim());
  const items = needle
    ? menu.items.filter((item) => searchable(item).includes(needle))
    : menu.items;

  function change(item: string, delta: number) {
    const quantity = Math.min(
      maximumQuantity,
      Math.max(0, (quantities[item] ?? 0) + delta),
    );
    const next = { ...quantities };

    if (quantity === 0) delete next[item];
    else next[item] = quantity;
    onChange(next);
  }

  return (
    <section aria-label="Pozycje z menu" className="flex flex-col gap-3">
      {menu.items.length > searchThreshold && (
        <FormTextField
          fullWidth
          // eslint-disable-next-line jsx-a11y/no-autofocus -- desktop staff type item names straight away; touch devices are excluded above.
          autoFocus={hasFinePointer}
          inputProps={{
            type: "search",
            onKeyDown: (event) => {
              if (event.key !== "Enter") return;
              event.preventDefault();
              if (items.length !== 1 || isDisabled) return;
              change(items[0], 1);
              setQuery("");
            },
          }}
          label="Szukaj w menu"
          name="menu-search"
          value={query}
          onChange={setQuery}
        />
      )}
      {items.length === 0 ? (
        <p className="py-6 text-sm secondary-text">Brak pasujących pozycji.</p>
      ) : (
        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((item) => {
            const quantity = quantities[item] ?? 0;

            return (
              <li
                key={item}
                className="order-item-tile relative"
                data-selected={quantity > 0 || undefined}
              >
                <Button
                  aria-label={
                    quantity > 0
                      ? `${item}, wybrano ${quantity}. Dodaj jedną`
                      : `Dodaj ${item}`
                  }
                  className="order-item-tile__button"
                  isDisabled={isDisabled || quantity >= maximumQuantity}
                  variant="tertiary"
                  onPress={() => change(item, 1)}
                >
                  <span className="break-words">{item}</span>
                </Button>
                {quantity > 0 && (
                  <>
                    <span
                      aria-hidden="true"
                      className="order-item-tile__quantity tabular-nums"
                    >
                      {quantity}
                    </span>
                    <Button
                      isIconOnly
                      aria-label={`Usuń jedną pozycję ${item}`}
                      className="order-item-tile__decrease"
                      isDisabled={isDisabled}
                      size="sm"
                      variant="danger-soft"
                      onPress={() => change(item, -1)}
                    >
                      <MinusIcon size={16} />
                    </Button>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

import type { Menu } from "@/src/api/menus";

import { Button } from "@heroui/react";
import { Minus as MinusIcon, Plus as PlusIcon } from "lucide-react";

export type ItemQuantities = Record<string, number>;

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
  function change(item: string, delta: number) {
    const quantity = Math.min(99, Math.max(0, (quantities[item] ?? 0) + delta));
    const next = { ...quantities };

    if (quantity === 0) delete next[item];
    else next[item] = quantity;
    onChange(next);
  }

  return (
    <section aria-label={`Pozycje z menu ${menu.name}`}>
      <p className="pb-2 text-sm secondary-text">{menu.name}</p>
      <ul className="flex max-h-72 flex-col overflow-y-auto">
        {menu.items.map((item) => {
          const quantity = quantities[item] ?? 0;

          return (
            <li
              key={item}
              className="flex min-h-12 items-center gap-3 border-t border-separator py-1"
            >
              <Button
                aria-pressed={quantity > 0}
                className="h-auto min-h-11 min-w-0 flex-1 justify-start whitespace-normal break-words text-left"
                isDisabled={isDisabled}
                variant={quantity > 0 ? "secondary" : "tertiary"}
                onPress={() => change(item, quantity > 0 ? -quantity : 1)}
              >
                {item}
              </Button>
              {quantity > 0 && (
                <>
                  <Button
                    isIconOnly
                    aria-label={`Usuń jedną pozycję ${item}`}
                    className="rounded-md"
                    isDisabled={isDisabled}
                    variant="tertiary"
                    onPress={() => change(item, -1)}
                  >
                    <MinusIcon size={18} />
                  </Button>
                  <span
                    aria-label={`Liczba: ${quantity}`}
                    className="w-6 text-center tabular-nums"
                  >
                    {quantity}
                  </span>
                </>
              )}
              <Button
                isIconOnly
                aria-label={`Dodaj pozycję ${item}`}
                className="rounded-md"
                isDisabled={isDisabled || quantity >= 99}
                variant="tertiary"
                onPress={() => change(item, 1)}
              >
                <PlusIcon size={18} />
              </Button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

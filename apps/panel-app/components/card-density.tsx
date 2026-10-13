"use client";

import { Button, Tooltip } from "@heroui/react";
import { GridFourIcon, GridNineIcon } from "@phosphor-icons/react";
import { useEffect, useSyncExternalStore } from "react";

const densities = ["compact", "default", "comfortable"] as const;
const storageKey = "kairos.card-density";

type CardDensity = (typeof densities)[number];

let current: CardDensity | undefined;
const listeners = new Set<() => void>();

function readDensity(): CardDensity {
  if (current) return current;
  try {
    const stored = window.localStorage.getItem(storageKey);

    current = densities.find((density) => density === stored) ?? "default";
  } catch {
    current = "default";
  }

  return current;
}

function writeDensity(density: CardDensity) {
  current = density;
  try {
    window.localStorage.setItem(storageKey, density);
  } catch {
    // The choice still applies for this page view.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);

  return () => listeners.delete(listener);
}

// One panel-wide choice; every .entity-card-grid follows the root attribute.
export function CardDensityControl() {
  const density = useSyncExternalStore(
    subscribe,
    readDensity,
    () => "default" as const,
  );
  const index = densities.indexOf(density);

  useEffect(() => {
    document.documentElement.dataset.cardDensity = density;
  }, [density]);

  return (
    <div className="ml-auto hidden items-center gap-1 sm:flex">
      <Tooltip delay={500}>
        <Tooltip.Trigger>
          <Button
            isIconOnly
            aria-label="Mniejsze karty"
            isDisabled={index === 0}
            variant="tertiary"
            onPress={() => writeDensity(densities[index - 1])}
          >
            <GridNineIcon size={20} />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content>Mniejsze karty</Tooltip.Content>
      </Tooltip>
      <Tooltip delay={500}>
        <Tooltip.Trigger>
          <Button
            isIconOnly
            aria-label="Większe karty"
            isDisabled={index === densities.length - 1}
            variant="tertiary"
            onPress={() => writeDensity(densities[index + 1])}
          >
            <GridFourIcon size={20} />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content>Większe karty</Tooltip.Content>
      </Tooltip>
    </div>
  );
}

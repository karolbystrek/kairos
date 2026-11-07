import { Chip } from "@heroui/react";

export type EntityStatus = {
  color: "success" | "warning" | "default";
  label: string;
};

// The ending agrees with the entity's grammatical gender: lokal -y, konto -e, integracja -a.
export function lifecycleStatus(
  status: string,
  ending: "y" | "e" | "a",
): EntityStatus {
  return status === "ENABLED"
    ? { color: "success", label: `Włączon${ending}` }
    : { color: "default", label: `Wyłączon${ending}` };
}

export function StatusChip({
  className,
  status,
}: {
  className?: string;
  status: EntityStatus;
}) {
  return (
    <Chip
      className={`shrink-0 ${className ?? ""}`}
      color={status.color}
      size="sm"
      variant="soft"
    >
      {status.label}
    </Chip>
  );
}

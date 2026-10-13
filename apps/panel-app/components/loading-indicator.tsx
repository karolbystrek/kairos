import { Spinner } from "@heroui/react";

export function LoadingIndicator({ label }: { label: string }) {
  return (
    <div className="flex justify-center py-8" role="status">
      <Spinner aria-hidden="true" size="sm" />
      <span className="sr-only">{label}</span>
    </div>
  );
}

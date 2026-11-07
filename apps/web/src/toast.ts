import { toast } from "@heroui/react";

// Errors stay twice as long as successes so staff can read the reason.
export function toastError(title: string, description: string) {
  toast.danger(title, { description, timeout: 8000 });
}

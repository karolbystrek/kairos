import type { OrderStatus } from "@/src/orders/order-status";

import { Alert } from "@heroui/react";

import { CustomerToolbar } from "@/components/customer-toolbar";

const statusCopy: Record<OrderStatus, string> = {
  IN_PREPARATION: "Przygotowujemy Twoje zamówienie",
  READY: "Gotowe do odbioru",
  COMPLETED: "Zamówienie zrealizowane",
  CANCELED: "Zamówienie anulowane",
};

export function OrderSummary({
  order,
  staleWarning,
  children,
}: {
  order: {
    label: string | null;
    number: number;
    status: OrderStatus;
    updatedAt: string;
  };
  staleWarning: string | null;
  children?: React.ReactNode;
}) {
  return (
    <section className="flex flex-1 flex-col">
      <CustomerToolbar />
      {staleWarning && (
        <Alert className="mt-4" status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>{staleWarning}</Alert.Title>
            <Alert.Description>
              Ostatni znany status z{" "}
              {new Date(order.updatedAt).toLocaleString("pl-PL")}.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      )}
      <div
        aria-live="polite"
        className="flex flex-1 flex-col items-center justify-center py-12 text-center sm:py-16"
      >
        <p className="order-number">
          <span className="sr-only">Zamówienie </span>#{order.number}
        </p>
        {order.label && (
          <p className="mt-1 max-w-md break-words text-sm secondary-text">
            {order.label}
          </p>
        )}
        <h1 className="status-title mt-4 max-w-[20ch]">
          {statusCopy[order.status]}
        </h1>
        {children}
      </div>
    </section>
  );
}

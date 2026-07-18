import { OrderTracker } from "./order-tracker";

export default async function TrackedOrderPage({
  params,
}: {
  params: Promise<{ trackingReference: string }>;
}) {
  const { trackingReference } = await params;

  return <OrderTracker trackingReference={trackingReference} />;
}

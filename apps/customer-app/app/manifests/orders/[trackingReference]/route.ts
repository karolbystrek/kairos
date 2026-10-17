import { isTrackingReference } from "@/src/orders/order-route";
import {
  createKairosManifest,
  getInstallationStartUrl,
} from "@/src/pwa/manifest";

export async function GET(
  _request: Request,
  {
    params,
  }: {
    params: Promise<{ trackingReference: string }>;
  },
) {
  const { trackingReference } = await params;

  if (!isTrackingReference(trackingReference)) {
    return new Response(null, { status: 404 });
  }

  return Response.json(
    createKairosManifest(getInstallationStartUrl(trackingReference)),
    {
      headers: {
        "Cache-Control": "private, no-store",
        "Content-Type": "application/manifest+json",
      },
    },
  );
}

import { createKairosManifest } from "@/src/pwa/manifest";
import { installationStartPath, isTrackingReference } from "@/src/routes";

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
    createKairosManifest(installationStartPath(trackingReference)),
    {
      headers: {
        "Cache-Control": "private, no-store",
        "Content-Type": "application/manifest+json",
      },
    },
  );
}

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

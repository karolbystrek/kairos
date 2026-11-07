import { createKairosManifest } from "@/src/pwa/manifest";
import { paths } from "@/src/routes";

export function GET() {
  return Response.json(createKairosManifest(paths.customerHome), {
    headers: { "Content-Type": "application/manifest+json" },
  });
}

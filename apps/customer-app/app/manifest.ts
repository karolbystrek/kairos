import type { MetadataRoute } from "next";

import { createKairosManifest } from "@/src/pwa/manifest";

export default function manifest(): MetadataRoute.Manifest {
  return createKairosManifest("/");
}

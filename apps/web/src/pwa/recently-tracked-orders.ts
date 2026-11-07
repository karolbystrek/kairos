import { z } from "zod";

const CONSUMED_INSTALLATION_BOOTSTRAPS_STORAGE_KEY =
  "kairos.consumed-installation-bootstraps";

const consumedInstallationBootstrapsPayloadSchema = z
  .object({
    version: z.literal(1),
    trackingReferences: z.array(z.string().min(1)),
  })
  .strict();

function getLocalStorage(): Storage | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function parseStoredValue<T>(
  value: string | null,
  schema: z.ZodType<T>,
): T | null {
  if (value === null) {
    return null;
  }

  try {
    const result = schema.safeParse(JSON.parse(value));

    return result.success ? result.data : null;
  } catch {
    return null;
  }
}

export function consumeInstallationBootstrap(
  trackingReference: string | null,
): string | null {
  if (!trackingReference) {
    return null;
  }

  const storage = getLocalStorage();

  if (!storage) {
    return null;
  }

  try {
    const storedPayload = parseStoredValue(
      storage.getItem(CONSUMED_INSTALLATION_BOOTSTRAPS_STORAGE_KEY),
      consumedInstallationBootstrapsPayloadSchema,
    );
    const consumedTrackingReferences = storedPayload?.trackingReferences ?? [];

    if (consumedTrackingReferences.includes(trackingReference)) {
      return null;
    }

    storage.setItem(
      CONSUMED_INSTALLATION_BOOTSTRAPS_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        trackingReferences: [
          trackingReference,
          ...consumedTrackingReferences,
        ].slice(0, 20),
      }),
    );

    return trackingReference;
  } catch {
    return null;
  }
}

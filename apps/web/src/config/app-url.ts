import { connection } from "next/server";

/** The public origin, read per request so one image serves any hostname. */
export async function appUrl(): Promise<URL> {
  await connection();
  const configured = process.env.APP_URL;

  if (!configured) {
    throw new Error("APP_URL is not configured.");
  }

  return new URL(configured);
}

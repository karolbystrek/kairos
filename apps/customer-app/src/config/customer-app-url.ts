import { connection } from "next/server";

/** The public site URL, read per request so one image serves any hostname. */
export async function customerAppUrl(): Promise<URL> {
  await connection();
  const configured = process.env.CUSTOMER_APP_URL;

  if (!configured) {
    throw new Error("CUSTOMER_APP_URL is not configured.");
  }

  return new URL(configured);
}

import { connection } from "next/server";

// Read per request so one image serves any hostname.
async function requiredUrl(name: string): Promise<string> {
  await connection();
  const value = process.env[name];

  if (!value) {
    throw new Error(`${name} is not configured.`);
  }

  return value;
}

export const customerAppUrl = () => requiredUrl("CUSTOMER_APP_URL");
export const panelAppUrl = () => requiredUrl("PANEL_APP_URL");

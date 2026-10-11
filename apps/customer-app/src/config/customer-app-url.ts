const configuredCustomerAppUrl = process.env.NEXT_PUBLIC_CUSTOMER_APP_URL;

if (!configuredCustomerAppUrl) {
  throw new Error("NEXT_PUBLIC_CUSTOMER_APP_URL is not configured.");
}

export const customerAppUrl = new URL(configuredCustomerAppUrl);

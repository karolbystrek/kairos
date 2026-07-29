function requiredEnvironmentVariable(
  name: string,
  value: string | undefined,
): string {
  if (!value) {
    throw new Error(`${name} is not configured.`);
  }

  return value;
}

export const apiBaseUrl = requiredEnvironmentVariable(
  "NEXT_PUBLIC_API_BASE_URL",
  process.env.NEXT_PUBLIC_API_BASE_URL,
);

export const customerAppUrl = requiredEnvironmentVariable(
  "NEXT_PUBLIC_CUSTOMER_APP_URL",
  process.env.NEXT_PUBLIC_CUSTOMER_APP_URL,
);

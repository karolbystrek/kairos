import { CustomerHome } from "./customer-home";

import { INSTALL_ORDER_QUERY_PARAMETER } from "@/src/pwa/manifest";

export default async function Home({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = await searchParams;
  const installationOrderValue =
    resolvedSearchParams[INSTALL_ORDER_QUERY_PARAMETER];
  const installationTrackingReference = Array.isArray(installationOrderValue)
    ? (installationOrderValue[0] ?? null)
    : (installationOrderValue ?? null);

  return (
    <CustomerHome
      installationTrackingReference={installationTrackingReference}
    />
  );
}

// Every URL path the app links to, redirects to or generates lives here.
export const paths = {
  home: "/",
  terms: "/regulamin",
  privacy: "/prywatnosc",
  panelPrivacy: "/panel/prywatnosc",
  // The trailing slash keeps the launcher inside the service worker scope.
  customerHome: "/o/",
  orderScanner: "/o/scan",
  offlineFallback: "/o/~offline",
  customerManifest: "/o/manifest.webmanifest",
  serviceWorker: "/o/sw.js",
  serviceWorkerScope: "/o/",
  login: "/panel/login",
  loginAfterPasswordChange: "/panel/login?password-changed",
  registration: "/panel/registration",
  passwordReset: "/panel/password-reset",
  passwordChange: "/panel/password",
} as const;

export const INSTALL_ORDER_QUERY_PARAMETER = "install-order";

const TRACKING_REFERENCE_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isTrackingReference(value: string): boolean {
  return TRACKING_REFERENCE_PATTERN.test(value);
}

export function orderTrackingPath(trackingReference: string): string {
  return `/o/${encodeURIComponent(trackingReference)}`;
}

export function orderManifestPath(trackingReference: string): string {
  return `${orderTrackingPath(trackingReference)}/manifest.webmanifest`;
}

export function installationStartPath(trackingReference: string): string {
  const searchParams = new URLSearchParams({
    [INSTALL_ORDER_QUERY_PARAMETER]: trackingReference,
  });

  return `${paths.customerHome}?${searchParams.toString()}`;
}

/** The tracking reference of an order tracking path, or null for any other path. */
export function parseOrderTrackingPath(pathname: string): string | null {
  const reference = pathname.match(/^\/o\/([^/]+)$/)?.[1];

  return reference && isTrackingReference(reference) ? reference : null;
}

export function parseScannedOrderUrl(
  value: string,
  customerOrigin = globalThis.location?.origin,
): string | null {
  if (!customerOrigin) {
    return null;
  }

  try {
    const candidate = new URL(value);

    if (
      candidate.origin !== customerOrigin ||
      candidate.username ||
      candidate.password ||
      candidate.search ||
      candidate.hash
    ) {
      return null;
    }

    return parseOrderTrackingPath(candidate.pathname);
  } catch {
    return null;
  }
}

export type PanelRoute =
  | { workspace: "orders"; locationId?: string; orderId?: string }
  | { workspace: "menus"; locationId?: string }
  | {
      workspace:
        | "locations"
        | "accounts"
        | "integrations"
        | "analytics"
        | "platform";
    };

export type PanelWorkspace = PanelRoute["workspace"];

export const defaultPanelRoute: PanelRoute = { workspace: "orders" };

const workspaceSegments: Record<PanelWorkspace, string> = {
  orders: "orders",
  menus: "menu",
  locations: "locations",
  accounts: "staff",
  integrations: "integrations",
  analytics: "analytics",
  platform: "platform",
};

const workspacesBySegment = new Map(
  Object.entries(workspaceSegments).map(([workspace, segment]) => [
    segment,
    workspace as PanelWorkspace,
  ]),
);

/** Orders and menus are scoped by `/locations/{id}`; other workspaces are tenant-wide. */
export function panelPath(route: PanelRoute): string {
  const scope =
    "locationId" in route && route.locationId
      ? `/locations/${route.locationId}`
      : "";
  const order = "orderId" in route && route.orderId ? `/${route.orderId}` : "";

  return `/panel${scope}/${workspaceSegments[route.workspace]}${order}`;
}

export function parsePanelPath(pathname: string): PanelRoute | null {
  const [root, ...segments] = pathname.split("/").filter(Boolean);

  if (root !== "panel") return null;

  return segments[0] === "locations" && segments.length > 2
    ? parseWorkspace(segments.slice(2), segments[1])
    : parseWorkspace(segments);
}

function parseWorkspace(
  segments: string[],
  locationId?: string,
): PanelRoute | null {
  const [segment = "", orderId, ...rest] = segments;
  const workspace = workspacesBySegment.get(segment);

  if (!workspace || rest.length > 0) return null;
  if (workspace === "orders") return { workspace, locationId, orderId };
  if (orderId !== undefined) return null;
  if (workspace === "menus") return { workspace, locationId };

  return locationId === undefined ? { workspace } : null;
}

import { apiFetch } from "./api-fetch";

export function recordPanelAction(
  event:
    | "queue_opened"
    | "menu_editor_opened"
    | "integration_settings_opened"
    | "order_creation_form_opened",
  locationId?: string,
) {
  void apiFetch(
    "/api/usage-analytics/v1/actions",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        actions: [{ event, ...(locationId ? { locationId } : {}) }],
      }),
    },
    { notifyUnauthorized: false },
  ).catch(() => {});
}

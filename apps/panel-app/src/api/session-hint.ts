// The API's session cookie is __Host- scoped to the API origin, so neither the
// panel's server nor its scripts can see it. This panel-origin flag records that
// this browser recently held a session, so anonymous visitors never call the API.
const key = "kairos.staff-session";

export function hasSessionHint(): boolean {
  try {
    return localStorage.getItem(key) === "1";
  } catch {
    return false;
  }
}

export function setSessionHint(signedIn: boolean): void {
  try {
    if (signedIn) localStorage.setItem(key, "1");
    else localStorage.removeItem(key);
  } catch {
    // Storage can be blocked; the landing page then simply never auto-redirects.
  }
}

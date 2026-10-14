import { useSyncExternalStore } from "react";

function subscribeConnectivity(changed: () => void) {
  window.addEventListener("online", changed);
  window.addEventListener("offline", changed);

  return () => {
    window.removeEventListener("online", changed);
    window.removeEventListener("offline", changed);
  };
}

export function useOnline(): boolean {
  return useSyncExternalStore(
    subscribeConnectivity,
    () => navigator.onLine,
    () => true,
  );
}

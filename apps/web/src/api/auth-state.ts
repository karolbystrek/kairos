const listeners = new Set<() => void>();

export function notifyAuthenticationRequired() {
  listeners.forEach((listener) => listener());
}
export function subscribeToAuthenticationRequired(listener: () => void) {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}

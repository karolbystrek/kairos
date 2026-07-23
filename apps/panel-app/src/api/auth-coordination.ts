const authenticationLockName = "kairos-auth-cookie-v1";

let inTabAuthenticationQueue: Promise<void> = Promise.resolve();
const authenticationRequiredListeners = new Set<() => void>();

function withInTabAuthenticationLock<T>(
  operation: () => Promise<T>,
): Promise<T> {
  const result = inTabAuthenticationQueue.then(operation, operation);

  inTabAuthenticationQueue = result.then(
    () => undefined,
    () => undefined,
  );

  return result;
}

export function withAuthCookieLock<T>(operation: () => Promise<T>): Promise<T> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return navigator.locks.request(authenticationLockName, operation);
  }

  return withInTabAuthenticationLock(operation);
}

export function notifyAuthenticationRequired() {
  authenticationRequiredListeners.forEach((listener) => listener());
}

export function subscribeToAuthenticationRequired(
  listener: () => void,
): () => void {
  authenticationRequiredListeners.add(listener);

  return () => authenticationRequiredListeners.delete(listener);
}

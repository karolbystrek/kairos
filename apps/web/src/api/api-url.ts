/** Aborts a REST request after 10 s so a stalled network reaches the error and stored-snapshot fallbacks. */
export function requestSignal(signal?: AbortSignal | null): AbortSignal {
  const timeout = AbortSignal.timeout(10_000);

  return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

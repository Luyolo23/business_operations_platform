import { useCallback, useEffect, useRef, useState } from 'react';

/** Tiny data-loading hook: runs `fn` on mount and whenever `deps` change. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  // A serialised dependency key lets us tell "the filters changed" apart from
  // "reload the same query": stale data must go in the first case but may stay
  // visible while refreshing in the second.
  const depsKey = JSON.stringify(deps);
  const lastDepsKey = useRef(depsKey);

  useEffect(() => {
    let cancelled = false;
    if (lastDepsKey.current !== depsKey) {
      lastDepsKey.current = depsKey;
      setData(null);
    }
    setLoading(true);
    setError(null);
    fn()
      .then((d) => { if (!cancelled) setData(d); })
      .catch((e: unknown) => { if (!cancelled) setError(e instanceof Error ? e.message : 'Something went wrong'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [depsKey, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading, reload };
}

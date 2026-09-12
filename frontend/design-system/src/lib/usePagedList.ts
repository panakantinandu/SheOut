import { useCallback, useEffect, useRef, useState } from 'react';

/** The shape every paged endpoint in this API returns - see PageResponse.java. */
export interface PagedResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  hasMore: boolean;
}

export interface PagedListState<T> {
  items: T[];
  total: number;
  hasMore: boolean;
  /** First load, or a reload after the filters changed. */
  loading: boolean;
  /** Appending the next page, with items already on screen. */
  loadingMore: boolean;
  error: string | null;
  loadMore: () => void;
  reload: () => void;
}

/**
 * Loads a paged list and appends further pages on demand.
 * <p>
 * `deps` is the filter state. Any change resets to page 0 and replaces the
 * items rather than appending, because appending page 0 of a narrower
 * filter onto page 1 of a wider one produces a list that never existed.
 * <p>
 * Two loading flags, not one. The first load and a filter change should
 * show the list as loading; fetching page 2 should not blank the rows
 * already on screen, which is what a single flag forces.
 * <p>
 * Every in-flight request is invalidated when the filters change again.
 * Debouncing alone does not prevent a slow earlier request landing after a
 * newer one and leaving the list showing results for a filter the person
 * has already moved away from - the same trap useFareQuote documents.
 */
export function usePagedList<T>(
  fetchPage: (page: number) => Promise<PagedResult<T>>,
  deps: unknown[],
  options?: { debounceMs?: number }
): PagedListState<T> {
  const debounceMs = options?.debounceMs ?? 0;

  const [items, setItems] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Bumped on every filter change and every manual reload. A response whose
  // generation is no longer current is discarded.
  const generation = useRef(0);
  const [reloadToken, setReloadToken] = useState(0);
  const fetchRef = useRef(fetchPage);
  fetchRef.current = fetchPage;

  const key = JSON.stringify(deps);

  useEffect(() => {
    const mine = ++generation.current;
    setLoading(true);
    setError(null);

    const run = async () => {
      try {
        const result = await fetchRef.current(0);
        if (generation.current !== mine) return;
        setItems(result.items);
        setTotal(result.totalItems);
        setHasMore(result.hasMore);
        setPage(0);
      } catch (err) {
        if (generation.current !== mine) return;
        setError(err instanceof Error ? err.message : 'Could not load this list');
      } finally {
        if (generation.current === mine) setLoading(false);
      }
    };

    if (debounceMs > 0) {
      const timer = setTimeout(run, debounceMs);
      return () => clearTimeout(timer);
    }
    run();
    return undefined;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, reloadToken, debounceMs]);

  const loadMore = useCallback(async () => {
    const mine = generation.current;
    const next = page + 1;
    setLoadingMore(true);
    try {
      const result = await fetchRef.current(next);
      if (generation.current !== mine) return;
      // Append, and keep the server's own totals - a filter change between
      // the tap and the response would otherwise mix two lists together.
      setItems((prev) => [...prev, ...result.items]);
      setTotal(result.totalItems);
      setHasMore(result.hasMore);
      setPage(next);
    } catch (err) {
      if (generation.current !== mine) return;
      setError(err instanceof Error ? err.message : 'Could not load more');
    } finally {
      if (generation.current === mine) setLoadingMore(false);
    }
  }, [page]);

  const reload = useCallback(() => setReloadToken((t) => t + 1), []);

  return { items, total, hasMore, loading, loadingMore, error, loadMore, reload };
}

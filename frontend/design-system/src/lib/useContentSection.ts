import { useEffect, useRef, useState } from 'react';
import type { FaqItem } from '../components/FaqList';

/**
 * How long a section of app copy is trusted before it is fetched again.
 * <p>
 * Five minutes: this is marketing and help text, not live data. An operator
 * who edits the Home banner sees it in the app within a few minutes, and a
 * rider opening Home twenty times a day costs one request, not twenty.
 */
export const CONTENT_REFRESH_MS = 5 * 60 * 1000;

const STORAGE_PREFIX = 'sheout.content.v1.';

interface CachedSection {
  fetchedAt: number;
  values: Record<string, string>;
}

function readCache(prefix: string): CachedSection | null {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + prefix);
    return raw ? (JSON.parse(raw) as CachedSection) : null;
  } catch {
    return null;
  }
}

function writeCache(prefix: string, values: Record<string, string>) {
  try {
    localStorage.setItem(STORAGE_PREFIX + prefix, JSON.stringify({ fetchedAt: Date.now(), values }));
  } catch {
    // Storage full or disabled - the copy still shows, it just refetches next time.
  }
}

/**
 * One section of editable app copy (every key under `prefix`), served by the
 * content module.
 * <p>
 * Stale-while-revalidate: whatever was cached is shown at once, and fetched
 * again if it is older than CONTENT_REFRESH_MS - on mount, on a timer while
 * the screen stays open, and when the app comes back to the foreground. A
 * failed fetch keeps what is already showing.
 * <p>
 * Returns an empty object until the first fetch ever succeeds on this device;
 * callers pass the text the block was seeded with as the fallback (see
 * contentText), so a first open with no network reads exactly as the app did
 * before this copy became editable - never blank.
 */
export function useContentSection(
  prefix: string,
  fetchSection: (prefix: string) => Promise<Record<string, string>>
): Record<string, string> {
  const [values, setValues] = useState<Record<string, string>>(() => readCache(prefix)?.values ?? {});
  const fetchRef = useRef(fetchSection);
  fetchRef.current = fetchSection;

  useEffect(() => {
    let cancelled = false;

    const refreshIfStale = () => {
      const cached = readCache(prefix);
      if (cached && Date.now() - cached.fetchedAt < CONTENT_REFRESH_MS) return;
      fetchRef.current(prefix)
        .then((fresh) => {
          writeCache(prefix, fresh);
          if (!cancelled) setValues(fresh);
        })
        .catch(() => {
          // Keep showing the cache or the fallback; try again next tick.
        });
    };

    refreshIfStale();
    const timer = window.setInterval(refreshIfStale, CONTENT_REFRESH_MS);
    const onVisible = () => {
      if (document.visibilityState === 'visible') refreshIfStale();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [prefix]);

  return values;
}

/** The edited text for a key, or the seeded fallback while none has been fetched. */
export function contentText(values: Record<string, string>, key: string, fallback: string): string {
  const value = values[key];
  return value && value.trim() ? value : fallback;
}

/**
 * FAQ items from numbered keys - `${prefix}1.question`, `${prefix}1.answer`,
 * `${prefix}2.question`, ... - in number order.
 * <p>
 * Falls back to the whole fallback list until the section has been fetched,
 * rather than mixing edited and seeded items. An item missing either half is
 * left out instead of rendering a question with no answer.
 */
export function faqItemsFromContent(values: Record<string, string>, prefix: string, fallback: FaqItem[]): FaqItem[] {
  const numbers = Object.keys(values)
    .map((key) => key.match(new RegExp(`^${prefix.replace(/\./g, '\\.')}(\\d+)\\.question$`))?.[1])
    .filter((n): n is string => Boolean(n))
    .map(Number)
    .sort((a, b) => a - b);
  if (numbers.length === 0) return fallback;
  return numbers
    .map((n) => ({ question: values[`${prefix}${n}.question`], answer: values[`${prefix}${n}.answer`] }))
    .filter((item): item is FaqItem => Boolean(item.question?.trim() && item.answer?.trim()));
}

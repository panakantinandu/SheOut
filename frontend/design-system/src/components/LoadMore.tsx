import { Button } from './Button';

export interface LoadMoreProps {
  /** How many are on screen now. */
  shown: number;
  /** How many match the current filters in total. */
  total: number;
  hasMore: boolean;
  loading: boolean;
  onLoadMore: () => void;
}

/**
 * The foot of a paged list: how much of it you are looking at, and a button
 * for the rest.
 * <p>
 * Load-more rather than numbered pages, for the two phone screens. These
 * are scrolling lists read newest-first, and a person looking for last
 * week's trip scrolls; sending them to "page 3" loses their place and their
 * scroll position. The ops console's table gets numbered pages instead,
 * where an operator is scanning a dense grid on a wide screen and wants to
 * jump.
 * <p>
 * The count is shown even when everything fits, because "12 of 12" is the
 * answer to "is that really all of them?" - which is the question a
 * filtered list invites.
 */
export function LoadMore({ shown, total, hasMore, loading, onLoadMore }: LoadMoreProps) {
  if (total === 0) return null;
  return (
    <div className="space-y-3 text-center">
      <p className="text-xs text-text-secondary">
        Showing {shown} of {total}
      </p>
      {hasMore && (
        <Button variant="secondary" fullWidth disabled={loading} onClick={onLoadMore}>
          {loading ? 'Loading...' : 'Load more'}
        </Button>
      )}
    </div>
  );
}

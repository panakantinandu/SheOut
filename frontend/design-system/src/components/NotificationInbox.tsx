import { Bell } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { PagedResult } from '../lib/usePagedList';
import { cn } from '../lib/cn';
import { Button } from './Button';
import { ListEmptyState } from './ListEmptyState';
import { LoadMore } from './LoadMore';
import { PullToRefresh } from './PullToRefresh';
import { SkeletonList } from './Skeleton';

export interface InboxItem {
  id: string;
  type: string;
  title: string;
  body: string | null;
  /** A path inside this app, or null. */
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface InboxPage {
  page: PagedResult<InboxItem>;
  unreadCount: number;
}

export interface NotificationInboxProps {
  fetchPage: (page: number) => Promise<InboxPage>;
  markRead: (id: string) => Promise<unknown>;
  markAllRead: () => Promise<unknown>;
  /** Called with the notification's link when it is tapped. */
  onOpen: (link: string) => void;
  /** Bumped by the screen to reload, e.g. when a push arrives while it is open. */
  refreshKey?: number;
}

function when(iso: string): string {
  const date = new Date(iso);
  const minutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 24 * 60) return `${Math.round(minutes / 60)} hr ago`;
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

/**
 * The in-app notification history: everything SheOut told this account,
 * whether or not a copy reached the phone. Unread entries are marked; tapping
 * one marks it read and opens what it is about.
 */
export function NotificationInbox({ fetchPage, markRead, markAllRead, onOpen, refreshKey = 0 }: NotificationInboxProps) {
  const [items, setItems] = useState<InboxItem[] | null>(null);
  const [total, setTotal] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [unread, setUnread] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);

  const load = useCallback(
    async (pageNumber: number) => {
      const current = ++generation.current;
      try {
        const result = await fetchPage(pageNumber);
        if (current !== generation.current) return;
        setItems((previous) => (pageNumber === 0 || !previous ? result.page.items : [...previous, ...result.page.items]));
        setTotal(result.page.totalItems);
        setHasMore(result.page.hasMore);
        setPage(pageNumber);
        setUnread(result.unreadCount);
        setError(null);
      } catch {
        if (current === generation.current) setError('Could not load your notifications.');
      }
    },
    [fetchPage]
  );

  useEffect(() => {
    void load(0);
  }, [load, refreshKey]);

  async function open(item: InboxItem) {
    if (!item.read) {
      setItems((previous) => previous?.map((i) => (i.id === item.id ? { ...i, read: true } : i)) ?? previous);
      setUnread((n) => Math.max(0, n - 1));
      void markRead(item.id).catch(() => undefined);
    }
    if (item.link) onOpen(item.link);
  }

  async function readAll() {
    setItems((previous) => previous?.map((i) => ({ ...i, read: true })) ?? previous);
    setUnread(0);
    await markAllRead().catch(() => undefined);
  }

  if (error && !items) return <p className="text-sm text-danger">{error}</p>;
  // The shape of the notifications about to arrive, rather than the word
  // "Loading" - see Skeleton.
  if (!items) return <SkeletonList rows={4} label="Loading your notifications" />;

  if (items.length === 0) {
    return (
      <ListEmptyState
        illustrated
        icon={<Bell />}
        title="Nothing yet"
        message="Updates about your trips and account will appear here."
      />
    );
  }

  return (
    <PullToRefresh onRefresh={() => load(0)}>
      <div className="space-y-3" data-testid="inbox">
      {unread > 0 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-text-secondary">{unread} unread</p>
          <Button size="md" variant="secondary" onClick={readAll}>
            Mark all read
          </Button>
        </div>
      )}
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          onClick={() => open(item)}
          className={cn(
            'flex w-full items-start gap-3 rounded-card p-4 text-left shadow-card transition-colors',
            item.read ? 'bg-surface' : 'bg-primary-light'
          )}
          data-testid="inbox-item"
          data-read={item.read}
        >
          <span
            className={cn('mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full', item.read ? 'bg-transparent' : 'bg-primary')}
            aria-hidden="true"
          />
          <span className="min-w-0 flex-1">
            <span className={cn('block text-text-primary', item.read ? 'font-medium' : 'font-semibold')}>
              {item.title}
              {!item.read && <span className="sr-only"> (unread)</span>}
            </span>
            {item.body && <span className="mt-0.5 block text-sm text-text-secondary">{item.body}</span>}
            <span className="mt-1 block text-xs text-text-secondary">{when(item.createdAt)}</span>
          </span>
        </button>
      ))}
      <LoadMore
        shown={items.length}
        total={total}
        hasMore={hasMore}
        loading={loadingMore}
        onLoadMore={async () => {
          setLoadingMore(true);
          await load(page + 1);
          setLoadingMore(false);
        }}
      />
      </div>
    </PullToRefresh>
  );
}

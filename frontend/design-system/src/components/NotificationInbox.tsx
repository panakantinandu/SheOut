import { BadgeCheck, Bell, IndianRupee, MapPinned, Megaphone, MessageCircle, ShieldAlert } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import type { PagedResult } from '../lib/usePagedList';
import { cn } from '../lib/cn';
import { ListEmptyState } from './ListEmptyState';
import { LoadMore } from './LoadMore';
import { PullToRefresh } from './PullToRefresh';
import { SkeletonList } from './Skeleton';
import { useTranslation } from 'react-i18next';

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

type Translate = (key: string, values?: Record<string, unknown>) => string;

/**
 * What a notification is about, which decides how it looks. Every type the
 * backend sends (notifications.internal.NotificationType) belongs to one;
 * anything unrecognised - an announcement, or a type added later - shows as
 * news rather than as a trip.
 */
type Category = 'booking' | 'payment' | 'safety' | 'support' | 'account' | 'news';

const CATEGORY_OF: Record<string, Category> = {
  BOOKING_REQUESTED: 'booking', BOOKING_ACCEPTED: 'booking', DRIVER_ARRIVING: 'booking', BOOKING_COMPLETED: 'booking',
  BOOKING_CANCELLED: 'booking', NO_DRIVERS_AVAILABLE: 'booking', DRIVER_OFFER: 'booking',
  PAYMENT_RECEIPT: 'payment', PAYOUT_PAID: 'payment', INCENTIVE_EARNED: 'payment',
  SOS_ALERT: 'safety', SOS_OPERATOR_ALERT: 'safety',
  SUPPORT_REPLY: 'support',
  ACCOUNT_VERIFIED: 'account', ACCOUNT_VERIFICATION_REJECTED: 'account', VERIFICATION_SUBMITTED: 'account',
};

/** Icon, and the tint / strong / edge colours from the depth scale (tokens.js). */
const LOOK: Record<Category, { icon: ReactNode; circle: string; edge: string; dot: string }> = {
  booking: { icon: <MapPinned />, circle: 'bg-primary-light text-primary', edge: 'bg-primary', dot: 'bg-primary' },
  payment: { icon: <IndianRupee />, circle: 'bg-accent-green-tint text-accent-green-strong', edge: 'bg-accent-green', dot: 'bg-accent-green' },
  safety: { icon: <ShieldAlert />, circle: 'bg-accent-red-tint text-accent-red-strong', edge: 'bg-danger', dot: 'bg-danger' },
  support: { icon: <MessageCircle />, circle: 'bg-accent-blue-tint text-accent-blue-strong', edge: 'bg-accent-blue', dot: 'bg-accent-blue' },
  account: { icon: <BadgeCheck />, circle: 'bg-accent-orange-tint text-accent-orange-strong', edge: 'bg-accent-orange', dot: 'bg-accent-orange' },
  news: { icon: <Megaphone />, circle: 'bg-accent-orange-tint text-accent-orange-strong', edge: 'bg-accent-orange', dot: 'bg-accent-orange' },
};

type Bucket = 'today' | 'yesterday' | 'earlier';

function bucketOf(iso: string, now = new Date()): Bucket {
  const at = new Date(iso).getTime();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  if (at >= startOfToday) return 'today';
  if (at >= startOfToday - 24 * 60 * 60 * 1000) return 'yesterday';
  return 'earlier';
}

/** Relative within today; a clock time yesterday; a date before that. */
function when(iso: string, bucket: Bucket, t: Translate): string {
  const date = new Date(iso);
  if (bucket === 'today') {
    const minutes = Math.round((Date.now() - date.getTime()) / 60000);
    if (minutes < 1) return t('inbox.justNow');
    if (minutes < 60) return t('inbox.minutesAgo', { count: minutes });
    return t('inbox.hoursAgo', { count: Math.round(minutes / 60) });
  }
  if (bucket === 'yesterday') return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

/**
 * The in-app notification history: everything SheOut told this account,
 * whether or not a copy reached the phone. Unread entries are marked; tapping
 * one marks it read and opens what it is about.
 */
export function NotificationInbox({ fetchPage, markRead, markAllRead, onOpen, refreshKey = 0 }: NotificationInboxProps) {
  const { t } = useTranslation('ds');
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
        if (current === generation.current) setError(t('inbox.loadError'));
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
  if (!items) return <SkeletonList rows={4} label={t('inbox.loading')} />;

  if (items.length === 0) {
    return (
      <ListEmptyState
        illustrated
        icon={<Bell />}
        title={t('inbox.emptyTitle')}
        message={t('inbox.emptyMessage')}
      />
    );
  }

  const groups = (['today', 'yesterday', 'earlier'] as Bucket[])
    .map((bucket) => ({ bucket, items: items.filter((item) => bucketOf(item.createdAt) === bucket) }))
    .filter((group) => group.items.length > 0);

  return (
    <PullToRefresh onRefresh={() => load(0)}>
      <div className="space-y-6" data-testid="inbox">
      {unread > 0 && (
        <div className="flex items-center justify-between">
          <span className="rounded-full bg-primary-light px-3 py-1 text-caption text-primary" data-testid="inbox-unread">
            {t('inbox.unread', { count: unread })}
          </span>
          <button type="button" onClick={readAll} className="rounded-full px-3 py-2 text-caption font-semibold text-primary hover:bg-primary-light">
            {t('inbox.markAllRead')}
          </button>
        </div>
      )}
      {groups.map((group) => (
        <section key={group.bucket} className="space-y-2" data-testid={'inbox-group-' + group.bucket}>
          <h2 className="px-1 text-caption uppercase tracking-widest text-text-secondary">{t('inbox.' + group.bucket)}</h2>
          <div className="divide-y divide-border overflow-hidden rounded-card bg-surface shadow-lift">
            {group.items.map((item) => {
              const category = CATEGORY_OF[item.type] ?? 'news';
              const look = LOOK[category];
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => open(item)}
                  className={cn(
                    'relative flex w-full items-start gap-4 px-4 py-4 text-left transition-colors active:bg-background',
                    !item.read && 'bg-primary-light/40'
                  )}
                  data-testid="inbox-item"
                  data-read={item.read}
                  data-category={category}
                >
                  {/* The unread edge, in the notification's own colour. */}
                  {!item.read && <span className={cn('absolute inset-y-3 left-0 w-1 rounded-r-full', look.edge)} aria-hidden="true" />}
                  <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-full [&_svg]:h-5 [&_svg]:w-5', look.circle, item.read && 'opacity-70')}>
                    {look.icon}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-start justify-between gap-2">
                      <span className={cn('font-heading text-card-title', item.read ? 'font-medium text-text-secondary' : 'text-text-primary')}>
                        {item.title}
                        <span className="sr-only">
                          {' ('}{t('inbox.category.' + category)}{!item.read ? ', ' + t('inbox.unreadMarker') : ''}{')'}
                        </span>
                      </span>
                      <span className="flex shrink-0 items-center gap-2 pt-px">
                        <span className="text-caption text-text-secondary">{when(item.createdAt, group.bucket, t)}</span>
                        {!item.read && <span className={cn('h-2 w-2 rounded-full', look.dot)} aria-hidden="true" />}
                      </span>
                    </span>
                    {item.body && <span className="mt-1 line-clamp-2 block text-sm text-text-secondary">{item.body}</span>}
                  </span>
                </button>
              );
            })}
          </div>
        </section>
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

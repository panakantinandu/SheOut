import { Bike, CalendarX, MapPinned, Package, SearchX, UtensilsCrossed } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  AmountText,
  Button,
  Card,
  DateRangeFields,
  IconCircle,
  ListEmptyState,
  ListFilterBar,
  LoadMore,
  SelectField,
  StatusBadge,
  TopHeader,
  bookingCategoryLabel,
  bookingStatusLabel,
  endOfDayIso,
  startOfDayIso,
  usePagedList,
} from '@sheout/design-system';
import type { DateRangeValue, StatusTone } from '@sheout/design-system';
import { bookingApi } from '../api/client';
import type { BookingCategory, BookingStatus } from '../api/types';

type Tab = 'ALL' | 'RIDES' | 'PARCELS' | 'FOOD';

/**
 * Which trips this screen is about. Home has three separate doors into it
 * and, until now, all three opened on the same undifferentiated list - Live
 * Track, History and the Bookings tab were three labels for one screen, so
 * two of them told the customer nothing the third did not.
 * <p>
 * They are genuinely different questions. "Is my ride coming" is about the
 * four in-flight statuses and wants the map. "What did I spend last month"
 * is about the two finished ones and wants dates and fares. The tab in the
 * bar is the unfiltered everything.
 */
type View = 'all' | 'live' | 'history';

const LIVE_STATUSES: BookingStatus[] = ['REQUESTED', 'MATCHED', 'ACCEPTED', 'IN_PROGRESS'];
const PAST_STATUSES: BookingStatus[] = ['COMPLETED', 'CANCELLED'];

const VIEW_COPY: Record<View, {
  title: string;
  /** Statuses the server is asked for. Empty means every status. */
  statuses: BookingStatus[];
  emptyTitle: string;
  emptyMessage: string;
  /** Only the live view offers per-row tracking, since only it has a trip to follow. */
  trackable: boolean;
}> = {
  all: {
    title: 'My Bookings',
    statuses: [],
    emptyTitle: 'No bookings yet',
    emptyMessage: 'Your rides and deliveries will appear here once you book your first one.',
    trackable: false,
  },
  live: {
    title: 'Live Tracking',
    statuses: LIVE_STATUSES,
    emptyTitle: 'Nothing in progress',
    emptyMessage: 'You have no trip running right now. Book a ride or a delivery and you can follow it on the map from here.',
    trackable: true,
  },
  history: {
    title: 'Trip History',
    statuses: PAST_STATUSES,
    emptyTitle: 'No past trips yet',
    emptyMessage: 'Trips you finish or cancel move here, so you can look back at what you paid.',
    trackable: false,
  },
};

// Food is left out for launch, alongside Home's Lunch Box tile: nothing can
// create a LUNCHBOX booking from this app, so the filter could only ever
// come back empty, and a permanently-empty filter reads as a broken one.
// The FOOD case stays in the type and in TAB_CATEGORIES so the tab returns
// by adding one line here when Lunch Box does.
const TABS: { key: Tab; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'RIDES', label: 'Rides' },
  { key: 'PARCELS', label: 'Parcels' },
];

const TAB_CATEGORIES: Record<Tab, BookingCategory[]> = {
  ALL: [],
  RIDES: ['BIKE', 'AUTO', 'CAB'],
  PARCELS: ['PARCEL'],
  FOOD: ['LUNCHBOX'],
};

const STATUS_OPTIONS: Record<View, { value: BookingStatus; label: string }[]> = {
  all: [
    { value: 'REQUESTED', label: 'Finding a partner' },
    { value: 'MATCHED', label: 'Partner assigned' },
    { value: 'ACCEPTED', label: 'On the way' },
    { value: 'IN_PROGRESS', label: 'In progress' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ],
  // Each view only offers the statuses it can actually contain. Offering
  // "Completed" inside Live Tracking would be a filter guaranteed to empty
  // the list.
  live: [
    { value: 'REQUESTED', label: 'Finding a partner' },
    { value: 'MATCHED', label: 'Partner assigned' },
    { value: 'ACCEPTED', label: 'On the way' },
    { value: 'IN_PROGRESS', label: 'In progress' },
  ],
  history: [
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ],
};

function statusTone(status: BookingStatus): StatusTone {
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  if (status === 'REQUESTED') return 'warning';
  return 'primary';
}

function categoryIcon(category: BookingCategory) {
  if (category === 'PARCEL') return <IconCircle color="orange" tone="soft" size="sm" icon={<Package />} />;
  if (category === 'LUNCHBOX') return <IconCircle color="green" tone="soft" size="sm" icon={<UtensilsCrossed />} />;
  return <IconCircle tone="soft" size="sm" icon={<Bike />} />;
}

/**
 * The customer's trips: searched, filtered and paged on the server, and
 * scoped to one of three views - see View above.
 * <p>
 * It used to fetch every booking this customer had ever made in one
 * request, then filter and sort the whole array in the browser.
 */
export function MyBookings() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const raw = params.get('view');
  const view: View = raw === 'live' || raw === 'history' ? raw : 'all';
  const copy = VIEW_COPY[view];

  const [tab, setTab] = useState<Tab>('ALL');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<BookingStatus | ''>('');
  const [dates, setDates] = useState<DateRangeValue>({ from: '', to: '' });

  const categories = TAB_CATEGORIES[tab];
  // A status picked in the filter panel narrows within the view; with none
  // picked, the view's own set applies. The view is a floor the filters
  // cannot get out from under, which is what makes the three doors stay
  // different once you are inside.
  const statuses = status ? [status] : copy.statuses;

  const fetchPage = useCallback(
    (page: number) =>
      bookingApi.search({
        page,
        status: statuses,
        from: startOfDayIso(dates.from),
        to: endOfDayIso(dates.to),
        category: categories,
        q: query.trim() || undefined,
      }),
    [statuses, dates.from, dates.to, categories, query]
  );

  const list = usePagedList(
    fetchPage,
    [view, tab, query, status, dates.from, dates.to],
    { debounceMs: 400 }
  );

  const activeFilters = useMemo(
    () => [status, dates.from, dates.to].filter(Boolean).length,
    [status, dates.from, dates.to]
  );
  const isNarrowed = activeFilters > 0 || query.trim().length > 0 || tab !== 'ALL';

  function clearAll() {
    setStatus('');
    setDates({ from: '', to: '' });
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={copy.title} onBack={() => navigate('/home')} />

      {view !== 'all' && (
        // Says which slice you are looking at, and offers the way to the
        // whole list. Without this, a filtered view that happens to be
        // short is indistinguishable from a short history.
        <Card tone="brand" className="flex items-center gap-3 py-3">
          <IconCircle tone="soft" size="sm" icon={view === 'live' ? <MapPinned /> : <CalendarX />} />
          <p className="flex-1 text-xs text-text-secondary">
            {view === 'live'
              ? 'Trips happening now. Finished trips are under History.'
              : 'Trips already finished or cancelled. Anything running now is under Live Track.'}
          </p>
          <Button variant="secondary" size="md" onClick={() => navigate('/bookings')}>
            See all
          </Button>
        </Card>
      )}

      <div className="flex gap-2 overflow-x-auto">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={
              tab === t.key
                ? 'shrink-0 rounded-full bg-primary px-4 py-1.5 text-sm font-semibold text-text-inverse'
                : 'shrink-0 rounded-full border border-border px-4 py-1.5 text-sm font-medium text-text-secondary'
            }
          >
            {t.label}
          </button>
        ))}
      </div>

      <ListFilterBar
        search={{ value: query, placeholder: 'Search pickup or drop', onChange: setQuery }}
        activeCount={activeFilters}
        onClearAll={clearAll}
      >
        <SelectField
          label="Status"
          placeholder="Any status"
          value={status}
          onChange={(e) => setStatus(e.target.value as BookingStatus | '')}
          options={STATUS_OPTIONS[view]}
        />
        <DateRangeFields value={dates} onChange={setDates} />
      </ListFilterBar>

      {list.error && <p className="text-sm text-danger">{list.error}</p>}
      {list.loading && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {!list.loading && list.items.length === 0 && !list.error && (
        // The two empty states mean different things and deliberately do not
        // share copy: one says "nothing here", the other says "your history
        // is intact, your filters are too narrow". Each view has its own
        // "nothing here" too, because an empty Live Track and an empty
        // history are not the same news.
        isNarrowed ? (
          <ListEmptyState
            icon={<SearchX />}
            title="No results match your filters"
            message="Your trips are still here. Try a wider date range, a different status, or clear the filters."
            action={{
              label: 'Clear filters and search',
              onClick: () => {
                clearAll();
                setQuery('');
                setTab('ALL');
              },
            }}
          />
        ) : (
          <ListEmptyState
            icon={view === 'live' ? <MapPinned /> : <CalendarX />}
            title={copy.emptyTitle}
            message={copy.emptyMessage}
            action={view === 'live' ? { label: 'Book a ride', onClick: () => navigate('/book/ride') } : undefined}
          />
        )
      )}

      <div className="space-y-3">
        {list.items.map((booking) => (
          <Card key={booking.id} className="flex items-center gap-3" onClick={() => navigate(`/tracking/${booking.id}`)}>
            {categoryIcon(booking.category)}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-text-primary">{bookingCategoryLabel(booking.category)}</p>
              <p className="truncate text-xs text-text-secondary">
                To {booking.drop.label} &middot; {new Date(booking.requestedAt).toLocaleString()}
              </p>
              <StatusBadge tone={statusTone(booking.status)} className="mt-1">
                {bookingStatusLabel(booking.status)}
              </StatusBadge>
            </div>
            {copy.trackable ? (
              // Named, not just a chevron. In the live view the useful thing
              // is the map, and the whole row already opens it - this says so.
              <span className="flex shrink-0 items-center gap-1 text-xs font-semibold text-primary">
                <MapPinned className="h-4 w-4" />
                Track
              </span>
            ) : (
              <AmountText amount={booking.finalFare ?? booking.fareEstimate} />
            )}
          </Card>
        ))}
      </div>

      <LoadMore
        shown={list.items.length}
        total={list.total}
        hasMore={list.hasMore}
        loading={list.loadingMore}
        onLoadMore={list.loadMore}
      />
    </div>
  );
}

import { Bike, CalendarX, Package, SearchX, UtensilsCrossed } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AmountText,
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

/**
 * Which categories each tab asks the server for. The tab is now a server
 * filter rather than an array filter over everything already downloaded -
 * which is what made it possible to page at all.
 */
const TAB_CATEGORIES: Record<Tab, BookingCategory[]> = {
  ALL: [],
  RIDES: ['BIKE', 'AUTO', 'CAB'],
  PARCELS: ['PARCEL'],
  FOOD: ['LUNCHBOX'],
};

const STATUS_OPTIONS: { value: BookingStatus; label: string }[] = [
  { value: 'REQUESTED', label: 'Finding a partner' },
  { value: 'MATCHED', label: 'Partner assigned' },
  { value: 'ACCEPTED', label: 'On the way' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

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
 * The customer's trip history: searched, filtered and paged on the server.
 * <p>
 * It used to fetch every booking this customer had ever made in one
 * request, then filter and sort the whole array in the browser. That is
 * fine for ten trips and progressively worse for a thousand, and it meant
 * the category tabs were the only narrowing available - no date, no status,
 * no searching for the address you half-remember.
 * <p>
 * Search is debounced through usePagedList rather than firing per
 * keystroke, and the two empty states say different things - see the
 * ListEmptyState below.
 */
export function MyBookings() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('ALL');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<BookingStatus | ''>('');
  const [dates, setDates] = useState<DateRangeValue>({ from: '', to: '' });

  const categories = TAB_CATEGORIES[tab];

  const fetchPage = useCallback(
    (page: number) =>
      bookingApi.search({
        page,
        status: status || undefined,
        from: startOfDayIso(dates.from),
        to: endOfDayIso(dates.to),
        category: categories,
        q: query.trim() || undefined,
      }),
    [status, dates.from, dates.to, categories, query]
  );

  const list = usePagedList(fetchPage, [tab, query, status, dates.from, dates.to], { debounceMs: 400 });

  /**
   * Counts what is narrowing the list, for the badge. The tab is not
   * included: it is always visible as a selected pill, so counting it would
   * claim a hidden filter that is not hidden.
   */
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
      <TopHeader variant="back" title="My Bookings" onBack={() => navigate('/home')} />

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
          options={STATUS_OPTIONS}
        />
        <DateRangeFields value={dates} onChange={setDates} />
      </ListFilterBar>

      {list.error && <p className="text-sm text-danger">{list.error}</p>}
      {list.loading && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {!list.loading && list.items.length === 0 && !list.error && (
        // The two empty states mean different things and deliberately do not
        // share copy: one says "you have not booked yet", the other says
        // "your history is intact, your filters are too narrow". Showing the
        // first to someone with years of trips reads as data loss.
        isNarrowed ? (
          <ListEmptyState
            icon={<SearchX />}
            title="No results match your filters"
            message="Your booking history is still here. Try a wider date range, a different status, or clear the filters."
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
            icon={<CalendarX />}
            title="No bookings yet"
            message="Your rides and deliveries will appear here once you book your first one."
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
            <AmountText amount={booking.finalFare ?? booking.fareEstimate} />
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

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

type Tab = 'ALL' | 'RIDES' | 'PARCELS';

const TABS: { key: Tab; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'RIDES', label: 'Rides' },
  { key: 'PARCELS', label: 'Parcels' },
];

/** Same grouping the rider app and the Earnings screen use, so a category cannot mean two things. */
const TAB_CATEGORIES: Record<Tab, BookingCategory[]> = {
  ALL: [],
  RIDES: ['BIKE', 'AUTO', 'CAB'],
  PARCELS: ['PARCEL', 'LUNCHBOX'],
};

const STATUS_OPTIONS: { value: BookingStatus; label: string }[] = [
  { value: 'MATCHED', label: 'Partner assigned' },
  { value: 'ACCEPTED', label: 'On the way' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

function statusTone(status: BookingStatus): StatusTone {
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  return 'primary';
}

function categoryIcon(category: BookingCategory) {
  if (category === 'PARCEL') return <IconCircle color="orange" tone="soft" size="sm" icon={<Package />} />;
  if (category === 'LUNCHBOX') return <IconCircle color="green" tone="soft" size="sm" icon={<UtensilsCrossed />} />;
  return <IconCircle tone="soft" size="sm" icon={<Bike />} />;
}

/**
 * A partner's trip history, searched, filtered and paged on the server.
 * <p>
 * The same treatment the rider app's list got, and for the same reason: a
 * working driver accumulates far more trips than a rider, so downloading
 * all of them to filter three tabs in the browser gets worse every week
 * they work. A partner looking for one trip to query a fare now has a
 * date range, a status and the address text to search by.
 */
export function Bookings() {
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
        isNarrowed ? (
          <ListEmptyState
            icon={<SearchX />}
            title="No results match your filters"
            message="Your trip history is still here. Try a wider date range, a different status, or clear the filters."
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
            title="No trips yet"
            message="Go online and the trips you complete will appear here."
          />
        )
      )}

      <div className="space-y-3">
        {list.items.map((booking) => (
          <Card
            key={booking.id}
            className="flex items-center gap-3"
            onClick={() => {
              if (['MATCHED', 'ACCEPTED', 'IN_PROGRESS'].includes(booking.status)) navigate(`/trip/${booking.id}`);
            }}
          >
            {categoryIcon(booking.category)}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-text-primary">{bookingCategoryLabel(booking.category)}</p>
              <p className="truncate text-xs text-text-secondary">
                {booking.pickup.label} to {booking.drop.label} &middot;{' '}
                {new Date(booking.requestedAt).toLocaleString()}
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

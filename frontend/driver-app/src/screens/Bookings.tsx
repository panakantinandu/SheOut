import { Bike, CalendarX, MessageCircle, Package, SearchX, Star, UtensilsCrossed } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AmountText,
  Card,
  DateRangeFields,
  IconCircle,
  ListEmptyState,
  PullToRefresh,
  SkeletonList,
  ListFilterBar,
  LoadMore,
  SelectField,
  StatusBadge,
  TopHeader,
  bookingCategoryLabel,
  isAwaitingPayment,
  tripStatusLabel,
  endOfDayIso,
  startOfDayIso,
  usePagedList,
} from '@sheout/design-system';
import type { DateRangeValue, StatusTone } from '@sheout/design-system';
import { bookingApi } from '../api/client';
import type { BookingCategory, BookingStatus, BookingSummary } from '../api/types';
import { RatingPrompt } from '../components/RatingPrompt';
import { useRatingMarks } from '../lib/useRatingMarks';

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
  // NO_DRIVERS_AVAILABLE is deliberately absent. A partner's history only
  // contains trips she was assigned, and a booking that ended with nobody
  // found was never assigned to anyone - so this filter could only ever
  // come back empty, and a permanently-empty filter reads as a broken one.
  // The tone function below still handles the status, because a status that
  // cannot appear today is not one to render badly if it ever does.
];

function statusTone(booking: BookingSummary): StatusTone {
  const { status } = booking;
  // Ended but unpaid is not a success yet - see isAwaitingPayment.
  if (isAwaitingPayment(booking)) return 'warning';
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  // Warning, not danger - nothing was anybody's fault here.
  if (status === 'NO_DRIVERS_AVAILABLE') return 'warning';
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
        status: status ? [status] : undefined,
        from: startOfDayIso(dates.from),
        to: endOfDayIso(dates.to),
        category: categories,
        q: query.trim() || undefined,
      }),
    [status, dates.from, dates.to, categories, query]
  );

  const list = usePagedList(fetchPage, [tab, query, status, dates.from, dates.to], { debounceMs: 400 });

  // So a trip already rated is marked, and one still open offers the way to
  // rate it from here - which is when a partner actually has a moment.
  const ratingMarks = useRatingMarks(list.items);
  const [ratingBookingId, setRatingBookingId] = useState<string | null>(null);

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
    <PullToRefresh onRefresh={list.reload} disabled={list.loading} className="space-y-6">
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
      {list.loading && <SkeletonList rows={4} label="Loading your trips" />}

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
            illustrated
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
              <StatusBadge tone={statusTone(booking)} className="mt-1">
                {tripStatusLabel(booking)}
              </StatusBadge>
              {/* A finished trip's chat is read-only but never deleted. If a
                  partner is ever accused of something that happened on a
                  trip, the thread is her account of it - so it has to stay
                  reachable from here, not only while the trip is live. */}
              {['COMPLETED', 'CANCELLED'].includes(booking.status) && (
                <button
                  type="button"
                  className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-primary"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate(`/chat/${booking.id}`);
                  }}
                >
                  <MessageCircle className="h-3.5 w-3.5" />
                  Messages
                </button>
              )}
              {/* Already rated, still rateable, or past its window - so
                  nobody is asked twice for the same trip. */}
              {booking.status === 'COMPLETED' && ratingMarks.has(booking.id) && (
                ratingMarks.get(booking.id)!.stars !== null ? (
                  <p className="mt-1 flex items-center gap-1 text-xs text-text-secondary">
                    <Star className="h-3.5 w-3.5 fill-accent-orange text-accent-orange" />
                    You rated this {ratingMarks.get(booking.id)!.stars} out of 5
                  </p>
                ) : new Date(ratingMarks.get(booking.id)!.rateableUntil) > new Date() ? (
                  <button
                    type="button"
                    className="mt-1 ml-3 inline-flex items-center gap-1 text-xs font-semibold text-primary"
                    onClick={(e) => {
                      e.stopPropagation();
                      setRatingBookingId(booking.id);
                    }}
                  >
                    <Star className="h-3.5 w-3.5" />
                    Rate this trip
                  </button>
                ) : null
              )}
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

      {/* Opened by the row's own "Rate this trip", so it is always about the
          trip she tapped. Keyed by booking id so reopening it for another
          trip starts clean. */}
      {ratingBookingId && (
        <RatingPrompt
          key={ratingBookingId}
          bookingId={ratingBookingId}
          counterpartLabel="your rider"
          onRated={() => {
            setRatingBookingId(null);
            list.reload();
          }}
        />
      )}
    </PullToRefresh>
  );
}

import { CalendarX, SearchX, Star, UtensilsCrossed } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import bikeTaxiImage from '../../../../public/BikeTaxiImage.png';
import parcelImage from '../../../../public/ParcelImage.png';
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
  bookingStatusLabel,
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
import { useTranslation } from '@sheout/design-system';

type Tab = 'ALL' | 'RIDES' | 'PARCELS';

const TABS: { key: Tab; label: string }[] = [
  { key: 'ALL', label: 'bookings.tabs.ALL' },
  { key: 'RIDES', label: 'bookings.tabs.RIDES' },
  { key: 'PARCELS', label: 'bookings.tabs.PARCELS' },
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
  if (category === 'PARCEL') {
    return (
      <div className="flex h-11 w-11 items-center justify-center overflow-hidden rounded-full bg-[#F3C385]/25 ring-1 ring-[#D98338]/20">
        <img src={parcelImage} alt="Parcel Delivery" className="h-7 w-7 object-contain drop-shadow-[0_4px_10px_rgba(0,0,0,0.18)]" />
      </div>
    );
  }
  if (category === 'LUNCHBOX') return <IconCircle color="green" tone="soft" size="sm" icon={<UtensilsCrossed />} />;
  return (
    <div className="flex h-11 w-11 items-center justify-center overflow-hidden rounded-full bg-[#DCC7FF]/25 ring-1 ring-[#8A6AE6]/20">
      <img src={bikeTaxiImage} alt="Bike Taxi" className="h-7 w-7 object-contain drop-shadow-[0_4px_10px_rgba(0,0,0,0.18)]" />
    </div>
  );
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
  const { t } = useTranslation();
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
      <TopHeader variant="back" title={t('bookings.title')} onBack={() => navigate('/home')} />

      <div className="flex gap-2 overflow-x-auto">
        {TABS.map((tabItem) => (
          <button
            key={tabItem.key}
            onClick={() => setTab(tabItem.key)}
            className={
              tab === tabItem.key
                ? 'shrink-0 rounded-full bg-primary px-4 py-1.5 text-sm font-semibold text-text-inverse'
                : 'shrink-0 rounded-full border border-border px-4 py-1.5 text-sm font-medium text-text-secondary'
            }
          >
            {t(tabItem.label)}
          </button>
        ))}
      </div>

      <ListFilterBar
        search={{ value: query, placeholder: t('bookings.searchPlaceholder'), onChange: setQuery }}
        activeCount={activeFilters}
        onClearAll={clearAll}
      >
        <SelectField
          label={t('bookings.status')}
          placeholder={t('bookings.anyStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value as BookingStatus | '')}
          options={STATUS_OPTIONS.map((o) => ({ value: o.value, label: bookingStatusLabel(o.value) }))}
        />
        <DateRangeFields value={dates} onChange={setDates} />
      </ListFilterBar>

      {list.error && <p className="text-sm text-danger">{list.error}</p>}
      {list.loading && <SkeletonList rows={4} label={t('bookings.loading')} />}

      {!list.loading && list.items.length === 0 && !list.error && (
        isNarrowed ? (
          <ListEmptyState
            icon={<SearchX />}
            title={t('bookings.noResults')}
            message={t('bookings.filteredEmpty')}
            action={{
              label: t('bookings.clearAll'),
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
            title={t('bookings.emptyTitle')}
            message={t('bookings.emptyMessage')}
          />
        )
      )}

      <div className="space-y-3">
        {list.items.map((booking) => (
          <Card
            key={booking.id}
            className="flex items-center gap-3"
            // Every trip opens: a live one as the job, a finished or
            // cancelled one as its record - fare, times, payment, rider and
            // messages. It used to open only live trips, so tapping any past
            // one did nothing at all.
            onClick={() => navigate(`/trip/${booking.id}`)}
            data-testid="booking-row"
          >
            {categoryIcon(booking.category)}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-text-primary">{bookingCategoryLabel(booking.category)}</p>
              <p className="truncate text-xs text-text-secondary">
                {t('bookings.fromTo', { from: booking.pickup.label, to: booking.drop.label })} &middot;{' '}
                {new Date(booking.requestedAt).toLocaleString()}
              </p>
              <StatusBadge tone={statusTone(booking)} className="mt-1">
                {tripStatusLabel(booking)}
              </StatusBadge>
              {/* A finished trip's chat is read-only but never deleted - if a
                  partner is ever accused of something that happened on a
                  trip, the thread is her account of it. It is reached from
                  the trip's own record (the message button on the rider
                  card), not from a link here: a small "Messages" link sat in
                  the middle of the row and took the tap meant for the trip. */}
              {/* Already rated, still rateable, or past its window - so
                  nobody is asked twice for the same trip. */}
              {booking.status === 'COMPLETED' && ratingMarks.has(booking.id) && (
                ratingMarks.get(booking.id)!.stars !== null ? (
                  <p className="mt-1 flex items-center gap-1 text-xs text-text-secondary">
                    <Star className="h-3.5 w-3.5 fill-accent-orange text-accent-orange" />
                    {t('bookings.youRated', { stars: ratingMarks.get(booking.id)!.stars })}
                  </p>
                ) : new Date(ratingMarks.get(booking.id)!.rateableUntil) > new Date() ? (
                  <button
                    type="button"
                    className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-primary"
                    onClick={(e) => {
                      e.stopPropagation();
                      setRatingBookingId(booking.id);
                    }}
                  >
                    <Star className="h-3.5 w-3.5" />
                    {t('bookings.rateTrip')}
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
          counterpartLabel={t('common.yourRider')}
          tripSummary={(() => {
            const trip = list.items.find((item) => item.id === ratingBookingId);
            return trip ? `${trip.pickup.label} → ${trip.drop.label}` : null;
          })()}
          onRated={() => {
            setRatingBookingId(null);
            list.reload();
          }}
        />
      )}
    </PullToRefresh>
  );
}

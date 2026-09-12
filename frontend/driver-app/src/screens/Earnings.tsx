import { Bike, Calendar, ChevronDown, Package, TrendingUp, UtensilsCrossed } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AmountText,
  Button,
  Card,
  DateRangeFields,
  IconCircle,
  ListFilterBar,
  LoadMore,
  SelectField,
  TopHeader,
} from '@sheout/design-system';
import type { DateRangeValue } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, BookingSummary } from '../api/types';

/**
 * The mockup groups earnings as Rides / Parcels / Lunch Box rather than by
 * raw category enum, so BIKE, AUTO and CAB collapse into one "Rides" row.
 * This is the same grouping the customer app's My Bookings tabs use, so a
 * category cannot mean one thing in one screen and another elsewhere.
 */
type EarningsGroup = 'RIDES' | 'PARCELS' | 'LUNCH_BOX';

const GROUP_LABEL: Record<EarningsGroup, string> = {
  RIDES: 'Rides',
  PARCELS: 'Parcels',
  LUNCH_BOX: 'Lunch Box',
};

function groupOf(category: BookingCategory): EarningsGroup {
  if (category === 'PARCEL') return 'PARCELS';
  if (category === 'LUNCHBOX') return 'LUNCH_BOX';
  return 'RIDES';
}

function groupIcon(group: EarningsGroup) {
  if (group === 'PARCELS') return <Package className="h-4 w-4" />;
  if (group === 'LUNCH_BOX') return <UtensilsCrossed className="h-4 w-4" />;
  return <Bike className="h-4 w-4" />;
}

type Period = 'TODAY' | 'WEEK' | 'ALL';

const PERIODS: { key: Period; label: string }[] = [
  { key: 'WEEK', label: 'This Week' },
  { key: 'TODAY', label: 'Today' },
  { key: 'ALL', label: 'All Time' },
];

/** How many detail rows appear at a time under View Details. */
const DETAIL_PAGE_SIZE = 10;

function startOfDay(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/** Monday as the first day, matching how a working week is usually read here. */
function startOfWeek(): Date {
  const d = startOfDay();
  const dayFromMonday = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - dayFromMonday);
  return d;
}

function periodStart(period: Period): Date | null {
  if (period === 'TODAY') return startOfDay();
  if (period === 'WEEK') return startOfWeek();
  return null;
}

/**
 * REAL, but derived rather than fetched directly: there's no payments/
 * earnings module on the backend, so this sums finalFare across the
 * driver's own COMPLETED bookings from GET /api/v1/bookings/me.
 * <p>
 * DELIBERATELY NOT PAGED, unlike every other history list in this app, and
 * this is the one place that trade-off goes the other way. The point of
 * this screen is a total. A paged total is a wrong total: it would show a
 * different number depending on how far the driver had scrolled, and a
 * partner checking what they earned this week would be given a figure that
 * silently grew as they tapped. So the fetch stays whole, every filter is
 * applied over the whole set, and only the trip list under View Details is
 * paged - in memory, since the rows are already here.
 * <p>
 * FLAGGED: the honest fix at real scale is an aggregate endpoint that
 * returns sums for a filter without returning the rows. That does not exist
 * on this backend, and inventing one was a bigger change than this pass.
 * Until then this download grows with a working driver's history.
 * <p>
 * The period preset, the custom date range and the work-type filter are all
 * applied client-side over that same response, on completedAt and category.
 */
export function Earnings() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<BookingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<Period>('WEEK');
  const [pickingPeriod, setPickingPeriod] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [dates, setDates] = useState<DateRangeValue>({ from: '', to: '' });
  const [group, setGroup] = useState<EarningsGroup | ''>('');
  /** How many detail rows are on screen. See DETAIL_PAGE_SIZE. */
  const [detailShown, setDetailShown] = useState(DETAIL_PAGE_SIZE);

  useEffect(() => {
    bookingApi
      .listMine()
      .then(setBookings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load earnings'));
  }, []);

  const { periodTotal, allTimeTotal, completedTrips, byGroup, trips } = useMemo(() => {
    const completed = (bookings ?? []).filter((b) => b.status === 'COMPLETED' && b.completedAt);
    // A custom range overrides the period preset when one is set - two date
    // filters both narrowing at once would leave a driver unable to tell
    // which one produced the number.
    const customFrom = dates.from ? new Date(`${dates.from}T00:00:00`) : null;
    // Inclusive to the end of the chosen day: "to today" means through
    // today, and the off-by-one there hides a whole day's earnings.
    const customTo = dates.to ? new Date(`${dates.to}T23:59:59.999`) : null;
    const from = customFrom ?? periodStart(period);
    const byDate = completed.filter((b) => {
      const at = new Date(b.completedAt!);
      if (from && at < from) return false;
      if (customTo && at > customTo) return false;
      return true;
    });
    const inPeriod = group ? byDate.filter((b) => groupOf(b.category) === group) : byDate;
    const fareOf = (b: BookingSummary) => b.finalFare ?? b.fareEstimate;

    const groupTotals = new Map<EarningsGroup, { amount: number; count: number }>();
    for (const b of inPeriod) {
      const g = groupOf(b.category);
      const entry = groupTotals.get(g) ?? { amount: 0, count: 0 };
      entry.amount += fareOf(b);
      entry.count += 1;
      groupTotals.set(g, entry);
    }

    return {
      periodTotal: inPeriod.reduce((sum, b) => sum + fareOf(b), 0),
      allTimeTotal: completed.reduce((sum, b) => sum + fareOf(b), 0),
      completedTrips: inPeriod.length,
      byGroup: Array.from(groupTotals.entries()),
      trips: inPeriod.slice().sort((a, b) => new Date(b.completedAt!).getTime() - new Date(a.completedAt!).getTime()),
    };
  }, [bookings, period, dates.from, dates.to, group]);

  const periodLabel = PERIODS.find((p) => p.key === period)!.label;

  const activeFilters = [group, dates.from, dates.to].filter(Boolean).length;
  const hasFilters = activeFilters > 0;

  function clearFilters() {
    setGroup('');
    setDates({ from: '', to: '' });
    setDetailShown(DETAIL_PAGE_SIZE);
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Earnings" onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!bookings && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {bookings && (
        <>
          {/* No search box: a partner searching their earnings by address is
              looking for a trip, which the Bookings screen does properly
              with a server-side query. Here the question is always "how
              much, over what period, for what kind of work". */}
          <ListFilterBar activeCount={activeFilters} onClearAll={clearFilters}>
            <SelectField
              label="Type of work"
              placeholder="All types"
              value={group}
              onChange={(e) => setGroup(e.target.value as EarningsGroup | '')}
              options={(['RIDES', 'PARCELS', 'LUNCH_BOX'] as EarningsGroup[]).map((g) => ({
                value: g,
                label: GROUP_LABEL[g],
              }))}
            />
            <DateRangeFields value={dates} onChange={setDates} label="Custom date range" />
            <p className="text-xs text-text-secondary">
              A custom range replaces the period button above it, so only one date filter is ever in effect.
            </p>
          </ListFilterBar>

          <Card variant="primary" className="space-y-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm opacity-90">Total Earnings</p>
                <AmountText amount={periodTotal} size="lg" tone="inverse" />
              </div>
              <button
                type="button"
                onClick={() => setPickingPeriod((v) => !v)}
                aria-expanded={pickingPeriod}
                className="flex shrink-0 items-center gap-1.5 rounded-full bg-text-inverse/15 px-3 py-1.5 text-xs font-semibold text-text-inverse"
              >
                {periodLabel}
                <ChevronDown className="h-3.5 w-3.5" />
              </button>
            </div>

            {pickingPeriod && (
              <div className="flex gap-2">
                {PERIODS.map((p) => (
                  <button
                    key={p.key}
                    type="button"
                    onClick={() => {
                      setPeriod(p.key);
                      setPickingPeriod(false);
                    }}
                    className={
                      p.key === period
                        ? 'flex-1 rounded-full bg-text-inverse px-3 py-1.5 text-xs font-semibold text-primary'
                        : 'flex-1 rounded-full border border-text-inverse/40 px-3 py-1.5 text-xs font-medium text-text-inverse'
                    }
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            )}
          </Card>

          <div className="grid grid-cols-2 gap-3">
            <Card className="flex items-center gap-3">
              <IconCircle tone="soft" icon={<TrendingUp />} />
              <div>
                <p className="text-xs text-text-secondary">All-Time</p>
                <AmountText amount={allTimeTotal} />
              </div>
            </Card>
            <Card className="flex items-center gap-3">
              <IconCircle tone="soft" color="green" icon={<Calendar />} />
              <div>
                <p className="text-xs text-text-secondary">Trips ({periodLabel})</p>
                <p className="font-heading font-semibold text-text-primary">{completedTrips}</p>
              </div>
            </Card>
          </div>

          {byGroup.length > 0 ? (
            <div>
              <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">By Category</h2>
              <Card className="divide-y divide-border p-0">
                {byGroup.map(([group, { amount, count }]) => (
                  <div key={group} className="flex items-center gap-3 p-4">
                    <IconCircle tone="soft" size="sm" icon={groupIcon(group)} />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-text-primary">{GROUP_LABEL[group]}</p>
                      <p className="text-xs text-text-secondary">{count} trip{count === 1 ? '' : 's'}</p>
                    </div>
                    <AmountText amount={amount} />
                  </div>
                ))}
              </Card>
            </div>
          ) : (
            <p className="text-center text-sm text-text-secondary">No completed trips in this period.</p>
          )}

          {/* "View Details" in the mockup has no destination screen behind
              it. Rather than a button that goes nowhere, it expands the
              individual trips making up the total above. */}
          <Button fullWidth variant={showDetails ? 'secondary' : 'primary'} onClick={() => setShowDetails((v) => !v)}>
            {showDetails ? 'Hide Details' : 'View Details'}
          </Button>

          {showDetails && (
            <>
              <Card className="divide-y divide-border p-0">
                {trips.length === 0 ? (
                  <p className="p-4 text-center text-sm text-text-secondary">
                    {hasFilters
                      ? 'No results match your filters.'
                      : `Nothing to show for ${periodLabel.toLowerCase()}.`}
                  </p>
                ) : (
                  trips.slice(0, detailShown).map((b) => (
                    <div key={b.id} className="flex items-center gap-3 p-4">
                      <IconCircle tone="soft" size="sm" icon={groupIcon(groupOf(b.category))} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-text-primary">{b.drop.label}</p>
                        <p className="text-xs text-text-secondary">{new Date(b.completedAt!).toLocaleString()}</p>
                      </div>
                      <AmountText amount={b.finalFare ?? b.fareEstimate} />
                    </div>
                  ))
                )}
              </Card>
              <LoadMore
                shown={Math.min(detailShown, trips.length)}
                total={trips.length}
                hasMore={detailShown < trips.length}
                loading={false}
                onLoadMore={() => setDetailShown((n) => n + DETAIL_PAGE_SIZE)}
              />
            </>
          )}

          <p className="text-center text-xs text-text-secondary">
            Totals are worked out from every trip you completed in this period, not just the ones listed.
          </p>
        </>
      )}
    </div>
  );
}

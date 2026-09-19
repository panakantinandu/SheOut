import { Bike, Calendar, ChevronDown, Package, TrendingUp, UtensilsCrossed, Wallet } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AmountText,
  PullToRefresh,
  SkeletonCard,
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
import { useTranslation } from '@sheout/design-system';

/**
 * The mockup groups earnings as Rides / Parcels / Lunch Box rather than by
 * raw category enum, so BIKE, AUTO and CAB collapse into one "Rides" row.
 * This is the same grouping the customer app's My Bookings tabs use, so a
 * category cannot mean one thing in one screen and another elsewhere.
 */
type EarningsGroup = 'RIDES' | 'PARCELS' | 'LUNCH_BOX';

// Labels live in the translations under earnings.group.<key>.

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

const PERIODS: Period[] = ['WEEK', 'TODAY', 'ALL'];

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
  const { t } = useTranslation();
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

  const load = useCallback(
    () =>
      bookingApi
        .listMine()
        .then(setBookings)
        .catch((err) => setError(err instanceof ApiError ? err.message : t('earnings.loadError'))),
    []
  );

  useEffect(() => {
    void load();
  }, [load]);

  const { periodTotal, allTimeTotal, completedTrips, byGroup, trips } = useMemo(() => {
    // Paid trips only - a fare the rider has not paid yet is not earned.
    const completed = (bookings ?? []).filter((b) => b.status === 'COMPLETED' && b.completedAt && b.paymentSettledAt);
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

  const periodLabel = t(`earnings.period.${period}`);

  const activeFilters = [group, dates.from, dates.to].filter(Boolean).length;
  const hasFilters = activeFilters > 0;

  function clearFilters() {
    setGroup('');
    setDates({ from: '', to: '' });
    setDetailShown(DETAIL_PAGE_SIZE);
  }

  return (
    // Pull down to reload: a trip completed on this phone minutes ago should
    // be one gesture away from showing up in the total.
    <PullToRefresh onRefresh={load} disabled={!bookings} className="space-y-6">
      <TopHeader variant="back" title={t('earnings.title')} onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!bookings && !error && <SkeletonCard lines={4} label={t('earnings.loading')} />}

      {bookings && (
        <>
          {/* No search box: a partner searching their earnings by address is
              looking for a trip, which the Bookings screen does properly
              with a server-side query. Here the question is always "how
              much, over what period, for what kind of work". */}
          <ListFilterBar activeCount={activeFilters} onClearAll={clearFilters}>
            <SelectField
              label={t('earnings.typeOfWork')}
              placeholder={t('earnings.allTypes')}
              value={group}
              onChange={(e) => setGroup(e.target.value as EarningsGroup | '')}
              options={(['RIDES', 'PARCELS', 'LUNCH_BOX'] as EarningsGroup[]).map((g) => ({
                value: g,
                label: t(`earnings.group.${g}`),
              }))}
            />
            <DateRangeFields value={dates} onChange={setDates} label={t('earnings.customRange')} />
            <p className="text-xs text-text-secondary">
              {t('earnings.customRangeNote')}
            </p>
          </ListFilterBar>

          <Card variant="primary" className="space-y-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm opacity-90">{t('earnings.total')}</p>
                <AmountText amount={periodTotal} size="lg" tone="inverse" animate />
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
                    key={p}
                    type="button"
                    onClick={() => {
                      setPeriod(p);
                      setPickingPeriod(false);
                    }}
                    className={
                      p === period
                        ? 'flex-1 rounded-full bg-text-inverse px-3 py-1.5 text-xs font-semibold text-primary'
                        : 'flex-1 rounded-full border border-text-inverse/40 px-3 py-1.5 text-xs font-medium text-text-inverse'
                    }
                  >
                    {t(`earnings.period.${p}`)}
                  </button>
                ))}
              </div>
            )}
          </Card>

          {/* The totals here are fares; what she can actually withdraw is the
              wallet, which nets out commission and cash she already holds. */}
          <Button fullWidth variant="secondary" icon={<Wallet className="h-4 w-4" />} onClick={() => navigate('/payouts')}>
            {t('earnings.walletPayouts')}
          </Button>

          <div className="grid grid-cols-2 gap-3">
            <Card className="flex items-center gap-3">
              <IconCircle tone="soft" icon={<TrendingUp />} />
              <div>
                <p className="text-xs text-text-secondary">{t('earnings.allTime')}</p>
                <AmountText amount={allTimeTotal} animate />
              </div>
            </Card>
            <Card className="flex items-center gap-3">
              <IconCircle tone="soft" color="green" icon={<Calendar />} />
              <div>
                <p className="text-xs text-text-secondary">{t('earnings.tripsIn', { period: periodLabel })}</p>
                <p className="font-heading font-semibold text-text-primary">{completedTrips}</p>
              </div>
            </Card>
          </div>

          {byGroup.length > 0 ? (
            <div>
              <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('earnings.byCategory')}</h2>
              <Card className="divide-y divide-border p-0">
                {byGroup.map(([group, { amount, count }]) => (
                  <div key={group} className="flex items-center gap-3 p-4">
                    <IconCircle tone="soft" size="sm" icon={groupIcon(group)} />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-text-primary">{t(`earnings.group.${group}`)}</p>
                      <p className="text-xs text-text-secondary">{t('earnings.tripCount', { count })}</p>
                    </div>
                    <AmountText amount={amount} />
                  </div>
                ))}
              </Card>
            </div>
          ) : (
            <p className="text-center text-sm text-text-secondary">{t('earnings.noneInPeriod')}</p>
          )}

          {/* "View Details" in the mockup has no destination screen behind
              it. Rather than a button that goes nowhere, it expands the
              individual trips making up the total above. */}
          <Button fullWidth variant={showDetails ? 'secondary' : 'primary'} onClick={() => setShowDetails((v) => !v)}>
            {showDetails ? t('earnings.hideDetails') : t('earnings.viewDetails')}
          </Button>

          {showDetails && (
            <>
              <Card className="divide-y divide-border p-0">
                {trips.length === 0 ? (
                  <p className="p-4 text-center text-sm text-text-secondary">
                    {hasFilters
                      ? t('earnings.noResults')
                      : t('earnings.nothingFor', { period: periodLabel })}
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
            {t('earnings.totalsNote')}
          </p>
        </>
      )}
    </PullToRefresh>
  );
}

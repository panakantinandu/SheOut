import { Bike, Calendar, ChevronDown, Package, TrendingUp, UtensilsCrossed } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Button, Card, IconCircle, TopHeader } from '@sheout/design-system';
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
 * The period filter is applied client-side, on completedAt, over that same
 * response. A date-range query parameter would mean widening the booking
 * API for one screen when the list is already in hand and driver-sized -
 * worth revisiting only if a driver's history ever outgrows one fetch.
 */
export function Earnings() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<BookingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<Period>('WEEK');
  const [pickingPeriod, setPickingPeriod] = useState(false);
  const [showDetails, setShowDetails] = useState(false);

  useEffect(() => {
    bookingApi
      .listMine()
      .then(setBookings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load earnings'));
  }, []);

  const { periodTotal, allTimeTotal, completedTrips, byGroup, trips } = useMemo(() => {
    const completed = (bookings ?? []).filter((b) => b.status === 'COMPLETED' && b.completedAt);
    const from = periodStart(period);
    const inPeriod = from ? completed.filter((b) => new Date(b.completedAt!) >= from) : completed;
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
  }, [bookings, period]);

  const periodLabel = PERIODS.find((p) => p.key === period)!.label;

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Earnings" onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!bookings && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {bookings && (
        <>
          <Card variant="primary" className="space-y-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm opacity-90">Total Earnings</p>
                <AmountText amount={periodTotal} size="lg" className="!text-text-inverse" />
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
            <Card className="divide-y divide-border p-0">
              {trips.length === 0 ? (
                <p className="p-4 text-center text-sm text-text-secondary">Nothing to show for {periodLabel.toLowerCase()}.</p>
              ) : (
                trips.map((b) => (
                  <div key={b.id} className="flex items-center gap-3 p-4">
                    <IconCircle tone="soft" size="sm" icon={groupIcon(groupOf(b.category))} />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-text-primary">{b.drop.label}</p>
                      <p className="text-xs text-text-secondary">{new Date(b.completedAt!).toLocaleString()}</p>
                    </div>
                    <AmountText amount={b.finalFare ?? b.fareEstimate} />
                  </div>
                ))
              )}
            </Card>
          )}

          <p className="text-center text-xs text-text-secondary">
            Totals are calculated from your completed trips - no separate payments/earnings module exists on the backend yet.
          </p>
        </>
      )}
    </div>
  );
}

import { Bike, Calendar, Package, TrendingUp, UtensilsCrossed } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Card, IconCircle, TopHeader } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, BookingSummary } from '../api/types';

function categoryIcon(category: BookingCategory) {
  if (category === 'PARCEL') return <Package className="h-4 w-4" />;
  if (category === 'LUNCHBOX') return <UtensilsCrossed className="h-4 w-4" />;
  return <Bike className="h-4 w-4" />;
}

function startOfDay(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * REAL, but derived rather than fetched directly: there's no payments/
 * earnings module on the backend (RAZORPAY_KEY_ID/SECRET are unset
 * placeholders - see render.yaml). This sums finalFare across the driver's
 * own COMPLETED bookings from GET /api/v1/bookings/me, which is real data,
 * just computed client-side instead of a dedicated /earnings endpoint that
 * doesn't exist yet.
 */
export function Earnings() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<BookingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bookingApi
      .listMine()
      .then(setBookings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load earnings'));
  }, []);

  const { todayTotal, allTimeTotal, completedTrips, byCategory } = useMemo(() => {
    const completed = (bookings ?? []).filter((b) => b.status === 'COMPLETED' && b.completedAt);
    const today = startOfDay();
    const todaySum = completed
      .filter((b) => new Date(b.completedAt!) >= today)
      .reduce((sum, b) => sum + (b.finalFare ?? b.fareEstimate), 0);
    const allSum = completed.reduce((sum, b) => sum + (b.finalFare ?? b.fareEstimate), 0);

    const categoryTotals = new Map<BookingCategory, { amount: number; count: number }>();
    for (const b of completed) {
      const entry = categoryTotals.get(b.category) ?? { amount: 0, count: 0 };
      entry.amount += b.finalFare ?? b.fareEstimate;
      entry.count += 1;
      categoryTotals.set(b.category, entry);
    }

    return {
      todayTotal: todaySum,
      allTimeTotal: allSum,
      completedTrips: completed.length,
      byCategory: Array.from(categoryTotals.entries()),
    };
  }, [bookings]);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Earnings" onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!bookings && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {bookings && (
        <>
          <Card variant="primary" className="text-center">
            <p className="text-sm opacity-90">Today's Earnings</p>
            <AmountText amount={todayTotal} size="lg" className="!text-text-inverse" />
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
                <p className="text-xs text-text-secondary">Completed Trips</p>
                <p className="font-heading font-semibold text-text-primary">{completedTrips}</p>
              </div>
            </Card>
          </div>

          {byCategory.length > 0 && (
            <div>
              <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">By Category</h2>
              <Card className="divide-y divide-border p-0">
                {byCategory.map(([category, { amount, count }]) => (
                  <div key={category} className="flex items-center gap-3 p-4">
                    <IconCircle tone="soft" size="sm" icon={categoryIcon(category)} />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-text-primary">{category}</p>
                      <p className="text-xs text-text-secondary">{count} trip{count === 1 ? '' : 's'}</p>
                    </div>
                    <AmountText amount={amount} />
                  </div>
                ))}
              </Card>
            </div>
          )}

          <p className="text-center text-xs text-text-secondary">
            Totals are calculated from your completed trips - no separate payments/earnings module exists on the backend yet.
          </p>
        </>
      )}
    </div>
  );
}

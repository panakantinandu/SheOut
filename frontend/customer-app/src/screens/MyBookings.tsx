import { Bike, Package, UtensilsCrossed } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Card, IconCircle, ListRow, StatusBadge, TopHeader, bookingStatusLabel } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, BookingStatus, BookingSummary } from '../api/types';

type Tab = 'ALL' | 'RIDES' | 'PARCELS' | 'FOOD';

// Food is left out for launch, alongside Home's Lunch Box tile: nothing can
// create a LUNCHBOX booking from this app, so the filter could only ever
// come back empty, and a permanently-empty filter reads as a broken one.
// The FOOD case stays in the type and in matchesTab so the tab returns by
// adding one line here when Lunch Box does.
const TABS: { key: Tab; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'RIDES', label: 'Rides' },
  { key: 'PARCELS', label: 'Parcels' },
];

function matchesTab(category: BookingCategory, tab: Tab): boolean {
  if (tab === 'ALL') return true;
  if (tab === 'RIDES') return category === 'BIKE' || category === 'AUTO' || category === 'CAB';
  if (tab === 'PARCELS') return category === 'PARCEL';
  return category === 'LUNCHBOX';
}

function statusTone(status: BookingStatus): StatusTone {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'CANCELLED':
      return 'danger';
    case 'REQUESTED':
      return 'warning';
    default:
      return 'primary';
  }
}

/** The mockup's row titles: the service, not the raw category enum. */
function categoryLabel(category: BookingCategory): string {
  if (category === 'PARCEL') return 'Parcel Delivery';
  if (category === 'LUNCHBOX') return 'Lunch Box Delivery';
  if (category === 'AUTO') return 'Auto Ride';
  if (category === 'CAB') return 'Cab Ride';
  return 'Bike Taxi';
}

function categoryIcon(category: BookingCategory) {
  if (category === 'PARCEL') return <IconCircle color="orange" tone="soft" size="sm" icon={<Package />} />;
  if (category === 'LUNCHBOX') return <IconCircle color="green" tone="soft" size="sm" icon={<UtensilsCrossed />} />;
  return <IconCircle tone="soft" size="sm" icon={<Bike />} />;
}

/** Fully real - fetches the customer's actual bookings from GET /api/v1/bookings/me. */
export function MyBookings() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<BookingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('ALL');

  useEffect(() => {
    bookingApi
      .listMine()
      .then(setBookings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load bookings'));
  }, []);

  const filtered = useMemo(
    () => (bookings ?? []).filter((b) => matchesTab(b.category, tab)).sort((a, b) => b.requestedAt.localeCompare(a.requestedAt)),
    [bookings, tab]
  );

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
                ? 'rounded-full bg-primary px-4 py-1.5 text-sm font-semibold text-text-inverse'
                : 'rounded-full border border-border px-4 py-1.5 text-sm font-medium text-text-secondary'
            }
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!bookings && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}
      {bookings && filtered.length === 0 && (
        <p className="text-center text-sm text-text-secondary">No bookings in this category yet.</p>
      )}

      <div className="space-y-3">
        {filtered.map((booking) => (
          <Card key={booking.id} className="flex items-center gap-3" onClick={() => navigate(`/tracking/${booking.id}`)}>
            {categoryIcon(booking.category)}
            <div className="min-w-0 flex-1">
              {/* Service name as the title, with the destination beneath it -
                  the mockup's shape. This used to title each row with the
                  drop label, so a list of trips read as a list of places and
                  gave no clue which were rides and which were parcels. */}
              <p className="truncate text-sm font-medium text-text-primary">{categoryLabel(booking.category)}</p>
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
    </div>
  );
}

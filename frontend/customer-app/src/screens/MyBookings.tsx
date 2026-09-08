import { Bike, Package, UtensilsCrossed } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Card, IconCircle, ListRow, StatusBadge, TopHeader } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, BookingStatus, BookingSummary } from '../api/types';

type Tab = 'ALL' | 'RIDES' | 'PARCELS' | 'FOOD';

const TABS: { key: Tab; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'RIDES', label: 'Rides' },
  { key: 'PARCELS', label: 'Parcels' },
  { key: 'FOOD', label: 'Food' },
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
              <p className="truncate text-sm font-medium text-text-primary">{booking.drop.label}</p>
              <p className="text-xs text-text-secondary">{new Date(booking.requestedAt).toLocaleString()}</p>
              <StatusBadge tone={statusTone(booking.status)} className="mt-1">
                {booking.status}
              </StatusBadge>
            </div>
            <AmountText amount={booking.finalFare ?? booking.fareEstimate} />
          </Card>
        ))}
      </div>
    </div>
  );
}

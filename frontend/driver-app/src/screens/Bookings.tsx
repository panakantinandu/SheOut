import { Bike, Package, UtensilsCrossed } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Card, IconCircle, StatusBadge, TopHeader } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, BookingStatus, BookingSummary } from '../api/types';

function statusTone(status: BookingStatus): StatusTone {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'CANCELLED':
      return 'danger';
    default:
      return 'primary';
  }
}

function categoryIcon(category: BookingCategory) {
  if (category === 'PARCEL') return <IconCircle color="orange" tone="soft" size="sm" icon={<Package />} />;
  if (category === 'LUNCHBOX') return <IconCircle color="green" tone="soft" size="sm" icon={<UtensilsCrossed />} />;
  return <IconCircle tone="soft" size="sm" icon={<Bike />} />;
}

/** Fully real - fetches the driver's actual bookings from GET /api/v1/bookings/me. */
export function Bookings() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<BookingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bookingApi
      .listMine()
      .then(setBookings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load bookings'));
  }, []);

  const sorted = useMemo(
    () => (bookings ?? []).slice().sort((a, b) => b.requestedAt.localeCompare(a.requestedAt)),
    [bookings]
  );

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="My Bookings" onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!bookings && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}
      {bookings && sorted.length === 0 && <p className="text-center text-sm text-text-secondary">No bookings yet.</p>}

      <div className="space-y-3">
        {sorted.map((booking) => (
          <Card
            key={booking.id}
            className="flex items-center gap-3"
            onClick={() => {
              if (['MATCHED', 'ACCEPTED', 'IN_PROGRESS'].includes(booking.status)) navigate(`/trip/${booking.id}`);
            }}
          >
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

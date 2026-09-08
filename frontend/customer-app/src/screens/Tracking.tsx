import { MessageCircle, Phone, Radio, ShieldAlert, Star } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, IconCircle, StatusBadge, TopHeader } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingSummary } from '../api/types';
import { MapPlaceholder } from '../components/MapPlaceholder';
import { mockAction } from '../lib/mockAction';

const POLL_INTERVAL_MS = 3000;

/**
 * REAL: booking status/driverId/fare, fetched by polling
 * GET /api/v1/bookings/{id} (no push/websocket exists, same poll-based
 * pattern dispatch itself uses for driver offers). Cancel is real too.
 * <p>
 * MOCK: driver name/photo/rating/vehicle, ETA, and live distance. There is
 * no customer-facing endpoint to look up another account's driver profile
 * (users only exposes self-service GET /users/driver/me - nothing lets a
 * customer read someone else's DriverProfileSummary by id), and dispatch
 * doesn't stream live position at all. So once a driverId is present, this
 * screen shows clearly-labeled placeholder driver details rather than
 * pretending driverId alone is enough to build the mockup's driver card.
 */
export function Tracking() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    if (!bookingId) return;
    let cancelled = false;

    async function poll() {
      try {
        const result = await bookingApi.getById(bookingId!);
        if (!cancelled) setBooking(result);
      } catch (err) {
        if (!cancelled) setError(err instanceof ApiError ? err.message : 'Could not load booking');
      }
    }

    poll();
    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [bookingId]);

  async function handleCancel() {
    if (!bookingId) return;
    setCancelling(true);
    try {
      await bookingApi.cancel(bookingId);
      navigate('/home', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel booking');
    } finally {
      setCancelling(false);
    }
  }

  const hasDriver = Boolean(booking?.driverId);
  const canCancel = booking && ['REQUESTED', 'MATCHED', 'ACCEPTED'].includes(booking.status);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={hasDriver ? 'On the Way' : 'Finding a Driver'} onBack={() => navigate('/home')} />

      <MapPlaceholder label="Live location tracking" />

      {error && <p className="text-sm text-danger">{error}</p>}

      {!booking ? (
        <p className="text-center text-sm text-text-secondary">Loading...</p>
      ) : !hasDriver ? (
        <Card className="text-center">
          <p className="font-heading font-semibold text-text-primary">Searching for a nearby driver...</p>
          <p className="mt-1 text-sm text-text-secondary">This usually takes under a minute.</p>
          <StatusBadge tone="warning" className="mt-3">
            {booking.status}
          </StatusBadge>
        </Card>
      ) : (
        <Card className="flex items-center gap-3">
          <IconCircle size="lg" tone="soft" icon={<Star />} />
          <div className="flex-1">
            {/* MOCK from here down - see file header comment */}
            <p className="font-heading font-semibold text-text-primary">Driver details unavailable (mock)</p>
            <p className="text-xs text-text-secondary">★ -- &middot; Bike &middot; Reg. unavailable</p>
          </div>
          <button
            className="rounded-full p-2 text-primary hover:bg-background"
            onClick={() => mockAction('Call driver', 'no real driver phone number available - see file header comment')}
          >
            <Phone className="h-5 w-5" />
          </button>
          <button
            className="rounded-full p-2 text-primary hover:bg-background"
            onClick={() => mockAction('Message driver', 'no real driver phone number available - see file header comment')}
          >
            <MessageCircle className="h-5 w-5" />
          </button>
        </Card>
      )}

      {booking && hasDriver && (
        <Card>
          <div className="flex justify-between text-sm">
            <div>
              <p className="text-text-secondary">Arriving in</p>
              <p className="font-heading font-semibold text-text-primary">-- min (mock)</p>
            </div>
            <div>
              <p className="text-text-secondary">Distance</p>
              <p className="font-heading font-semibold text-text-primary">-- km (mock)</p>
            </div>
          </div>
        </Card>
      )}

      <div className="flex justify-around">
        <button
          className="flex flex-col items-center gap-1 text-xs text-text-secondary"
          onClick={() => mockAction('Share Live location', 'no live-location-sharing backend yet')}
        >
          <IconCircle tone="soft" icon={<Radio />} />
          Share Live
        </button>
        <button
          className="flex flex-col items-center gap-1 text-xs text-danger"
          onClick={() => navigate('/sos')}
        >
          <IconCircle color="red" tone="soft" icon={<ShieldAlert />} />
          SOS
        </button>
        <button
          className="flex flex-col items-center gap-1 text-xs text-text-secondary"
          onClick={() => mockAction('Call driver', 'no real driver phone number available')}
        >
          <IconCircle tone="soft" icon={<Phone />} />
          Call
        </button>
      </div>

      {canCancel && (
        <Button variant="danger" fullWidth disabled={cancelling} onClick={handleCancel}>
          {cancelling ? 'Cancelling...' : 'Cancel Ride'}
        </Button>
      )}
    </div>
  );
}

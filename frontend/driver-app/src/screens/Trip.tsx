import { Navigation, Phone } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, LiveMap, StatusBadge, TopHeader } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingSummary } from '../api/types';
import { mockAction } from '../lib/mockAction';
import { useLocationBroadcast } from '../lib/useLocationBroadcast';

const POLL_INTERVAL_MS = 4000;

/**
 * REAL: booking status/pickup/drop/fare, polled from GET /bookings/{id}
 * (same poll-based pattern as everything else here - no push backend
 * exists). Start/Complete/Cancel are real state transitions.
 * <p>
 * REAL: the map. Pickup and drop come from the booking; "You" is this
 * device's own GPS via the browser's geolocation API, not a round trip
 * through the backend - the driver already knows where they are, so
 * reading their own position back from dispatch would only add latency and
 * a failure mode. Position updates only when the browser reports real
 * movement; nothing here interpolates between fixes.
 * <p>
 * MOCK: customer name/phone - there's no driver-facing endpoint to look up
 * another account's customer profile by id (mirrors the same gap flagged
 * in customer-app's Tracking screen, just the other direction).
 * <p>
 * FLAGGED: the backend has no "arrived at pickup" state - only accept,
 * start, complete, cancel - so this screen has exactly those four actions,
 * gated by the booking's current status.
 */
export function Trip() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Keep broadcasting for the whole live trip, not just while on Home -
  // this is exactly when the customer's tracking map is watching. The hook
  // both sends the position and hands it back for the marker below, so
  // there is only one GPS subscription.
  const tripIsLive = booking ? ['ACCEPTED', 'IN_PROGRESS'].includes(booking.status) : false;
  const location = useLocationBroadcast(tripIsLive);
  const myPosition = location.position;

  useEffect(() => {
    if (!bookingId) return;
    let cancelled = false;

    async function poll() {
      try {
        const result = await bookingApi.getById(bookingId!);
        if (!cancelled) setBooking(result);
      } catch (err) {
        if (!cancelled) setError(err instanceof ApiError ? err.message : 'Could not load trip');
      }
    }

    poll();
    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [bookingId]);

  async function runAction(action: (id: string) => Promise<BookingSummary>) {
    if (!bookingId) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await action(bookingId);
      setBooking(updated);
      if (updated.status === 'COMPLETED' || updated.status === 'CANCELLED') {
        navigate('/home', { replace: true });
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  }

  const markers: MapMarker[] = [];
  if (booking) {
    markers.push({ key: 'pickup', lat: booking.pickup.lat, lng: booking.pickup.lng, label: 'Pickup', kind: 'pickup' });
    markers.push({ key: 'drop', lat: booking.drop.lat, lng: booking.drop.lng, label: 'Drop', kind: 'drop' });
  }
  if (myPosition) {
    markers.push({ key: 'me', lat: myPosition.lat, lng: myPosition.lng, label: 'You', kind: 'driver' });
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Trip" onBack={() => navigate('/home')} />

      <div className="space-y-1">
        <LiveMap markers={markers} />
        <p className="text-xs text-text-secondary">
          {myPosition
            ? 'Your position updates as your device reports movement.'
            : location.error ?? 'Finding your location...'}
        </p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!booking && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {booking && (
        <>
          <Card className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="font-heading font-semibold text-text-primary">{booking.type === 'RIDE' ? 'Ride' : 'Delivery'}</p>
              <StatusBadge tone="primary">{booking.status.replace('_', ' ')}</StatusBadge>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex items-start gap-2">
                <Navigation className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                <span className="text-text-primary">{booking.pickup.label}</span>
              </div>
              <div className="flex items-start gap-2">
                <Navigation className="mt-0.5 h-4 w-4 shrink-0 text-accent-orange" />
                <span className="text-text-primary">{booking.drop.label}</span>
              </div>
            </div>
            <div className="flex justify-between border-t border-border pt-3 text-sm">
              <span className="text-text-secondary">Fare</span>
              <span className="font-heading font-semibold text-text-primary">₹{booking.finalFare ?? booking.fareEstimate}</span>
            </div>
          </Card>

          <Card className="flex items-center justify-between">
            {/* MOCK: no endpoint exists for a driver to look up the customer's profile by id. */}
            <div>
              <p className="font-heading font-semibold text-text-primary">Customer details unavailable (mock)</p>
              <p className="text-xs text-text-secondary">No customer lookup endpoint on the backend</p>
            </div>
            <button
              className="rounded-full p-2 text-primary hover:bg-background"
              onClick={() => mockAction('Call customer', 'no customer phone lookup available for drivers')}
            >
              <Phone className="h-5 w-5" />
            </button>
          </Card>

          <div className="space-y-3">
            {booking.status === 'ACCEPTED' && (
              <Button fullWidth disabled={busy} onClick={() => runAction(bookingApi.start)}>
                {busy ? 'Starting...' : 'Start Trip'}
              </Button>
            )}
            {booking.status === 'IN_PROGRESS' && (
              <Button fullWidth variant="success" disabled={busy} onClick={() => runAction(bookingApi.complete)}>
                {busy ? 'Completing...' : 'Complete Trip'}
              </Button>
            )}
            {(booking.status === 'MATCHED' || booking.status === 'ACCEPTED') && (
              <Button fullWidth variant="danger" disabled={busy} onClick={() => runAction(bookingApi.cancel)}>
                {busy ? 'Cancelling...' : 'Cancel Trip'}
              </Button>
            )}
          </div>
        </>
      )}
    </div>
  );
}

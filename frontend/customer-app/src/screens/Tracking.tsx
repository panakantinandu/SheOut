import { CheckCircle2, MessageCircle, Phone, Radio, ShieldAlert, Star, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AmountText, Button, Card, IconCircle, LiveMap, StatusBadge, TopHeader, bookingStatusLabel } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi, dispatchApi } from '../api/client';
import type { BookingSummary, DriverLocation } from '../api/types';
import { mockAction } from '../lib/mockAction';

const POLL_INTERVAL_MS = 3000;
// Matches driver-app's LOCATION_SEND_MS exactly - polling faster than the
// driver broadcasts just re-fetches a position we already have.
const DRIVER_LOCATION_POLL_MS = 7000;

/**
 * REAL: booking status/driverId/fare, fetched by polling
 * GET /api/v1/bookings/{id} (no push/websocket exists, same poll-based
 * pattern dispatch itself uses for driver offers). Cancel is real too.
 * <p>
 * REAL: the map. Pickup/drop come from the booking; the driver marker is
 * the last position dispatch actually received, polled from
 * GET /api/v1/dispatch/bookings/{id}/driver-location.
 * <p>
 * This is NEAR-real-time, not real-time. The driver app broadcasts on an
 * interval and this polls on the same one, so the marker is up to ~7s
 * behind and moves in steps rather than gliding. Nothing here interpolates
 * between fixes: a smooth marker would be drawing the driver where they
 * have not actually been. Genuine real-time needs a WebSocket/SSE push
 * channel, which does not exist in this backend.
 * <p>
 * MOCK: driver name/photo/rating/vehicle and ETA. There is no
 * customer-facing endpoint to look up another account's driver profile
 * (users only exposes self-service GET /users/driver/me), so once a
 * driverId is present this screen shows clearly-labeled placeholder driver
 * details rather than pretending driverId alone is enough.
 */
export function Tracking() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [driverLocation, setDriverLocation] = useState<DriverLocation | null>(null);

  /** Set once the trip reaches a status that can never change again. */
  const terminalStatus = booking?.status === 'CANCELLED' || booking?.status === 'COMPLETED';

  useEffect(() => {
    if (!bookingId || terminalStatus) return;
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
    // terminalStatus in the deps, not the whole booking: re-subscribing on
    // every poll would defeat the interval. Once the trip is finished the
    // effect tears its timer down and never sets another.
  }, [bookingId, terminalStatus]);

  // Driver position, polled only once a driver is actually assigned - before
  // that the endpoint has nothing to return and would 404 on every tick.
  useEffect(() => {
    if (!bookingId || !booking?.driverId || terminalStatus) return;
    let cancelled = false;

    async function pollLocation() {
      try {
        const location = await dispatchApi.getDriverLocation(bookingId!);
        if (!cancelled) setDriverLocation(location);
      } catch {
        // 404 while the driver has not reported yet is normal, not an error
        // worth surfacing - the marker simply stays absent until one arrives.
      }
    }

    pollLocation();
    const interval = setInterval(pollLocation, DRIVER_LOCATION_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [bookingId, booking?.driverId, terminalStatus]);

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

  /**
   * A booking that has finished, one way or the other. Nothing about it is
   * going to change again.
   * <p>
   * This screen used to branch on one thing only - whether a driver was
   * assigned - so a cancelled trip fell into the same arm as a brand new
   * one and sat there saying "Searching for a nearby driver... this usually
   * takes under a minute", under a badge that said Cancelled. It also kept
   * polling the booking every 3 seconds and the driver's position every 7,
   * forever, for a trip that no longer existed.
   */
  const isFinished = terminalStatus;
  const hasDriver = Boolean(booking?.driverId) && !isFinished;
  const markers: MapMarker[] = [];
  if (booking) {
    markers.push({ key: 'pickup', lat: booking.pickup.lat, lng: booking.pickup.lng, label: 'Pickup', kind: 'pickup' });
    markers.push({ key: 'drop', lat: booking.drop.lat, lng: booking.drop.lng, label: 'Drop', kind: 'drop' });
  }
  if (driverLocation) {
    markers.push({ key: 'driver', lat: driverLocation.lat, lng: driverLocation.lng, label: 'Driver', kind: 'driver' });
  }
  const canCancel = booking && ['REQUESTED', 'MATCHED', 'ACCEPTED'].includes(booking.status);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={isFinished ? (booking?.status === 'CANCELLED' ? 'Trip Cancelled' : 'Trip Completed') : hasDriver ? 'On the Way' : 'Finding a Driver'} onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}

      {!booking ? (
        <p className="text-center text-sm text-text-secondary">Loading...</p>
      ) : isFinished ? (
        <Card
          tone={booking.status === 'CANCELLED' ? 'danger' : 'success'}
          className="flex items-start gap-3"
        >
          <IconCircle
            size="lg"
            tone="soft"
            color={booking.status === 'CANCELLED' ? 'red' : 'green'}
            icon={booking.status === 'CANCELLED' ? <XCircle /> : <CheckCircle2 />}
          />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">
              {booking.status === 'CANCELLED' ? 'This trip was cancelled' : 'Trip completed'}
            </p>
            <p className="mt-1 text-sm text-text-secondary">
              {booking.status === 'CANCELLED'
                ? 'No driver is on the way. Book again whenever you are ready.'
                : 'Thanks for riding with SheOut.'}
            </p>
            <Button size="md" variant="secondary" className="mt-3" onClick={() => navigate('/home')}>
              {booking.status === 'CANCELLED' ? 'Book another ride' : 'Back to home'}
            </Button>
          </div>
        </Card>
      ) : !hasDriver ? (
        <Card className="text-center">
          <p className="font-heading font-semibold text-text-primary">Searching for a nearby driver...</p>
          <p className="mt-1 text-sm text-text-secondary">This usually takes under a minute.</p>
          <StatusBadge tone="warning" className="mt-3">
            {bookingStatusLabel(booking.status)}
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
            onClick={() => mockAction('Call driver', "your partner's number is not shared here yet")}
          >
            <Phone className="h-5 w-5" />
          </button>
          <button
            className="rounded-full p-2 text-primary hover:bg-background"
            onClick={() => mockAction('Message driver', "your partner's number is not shared here yet")}
          >
            <MessageCircle className="h-5 w-5" />
          </button>
        </Card>
      )}

      <div className="space-y-1">
        <LiveMap markers={markers} />
        <p className="text-xs text-text-secondary">
          {isFinished
            ? 'Where this trip would have started and ended.'
            : driverLocation
              ? `Driver position updated ${secondsAgo(driverLocation.recordedAt)}s ago - refreshes every ${DRIVER_LOCATION_POLL_MS / 1000}s`
              : hasDriver
                ? 'Waiting for the driver to report a position...'
                : 'Showing your pickup and drop. The driver appears once one is assigned.'}
        </p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {/* REAL: fareEstimate comes back from bookingApi.create() at RideBooking/
          DeliveryBooking time (RequestBookingCommand runs FareCalculator
          immediately, there's no separate quote step - see those screens'
          file comments) - this is the first place it's actually shown.
          Sits under the map, where the mockup puts its arriving/distance
          strip - this app has no ETA to show there. */}
      {booking && (
        <Card className="flex items-center justify-between">
          {/* A cancelled trip was never charged. Showing a rupee figure
              with no qualifier reads as a bill. */}
          <span className="text-sm text-text-secondary">
            {booking.status === 'COMPLETED'
              ? 'Final Fare'
              : booking.status === 'CANCELLED'
                ? 'Estimated fare - not charged'
                : 'Estimated Fare'}
          </span>
          <AmountText amount={booking.finalFare ?? booking.fareEstimate} size="lg" />
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

      {/* Hidden once the trip is over. Sharing a live location for a
          cancelled trip, or offering to call a driver who was never coming,
          are both offers of something that does not exist. SOS stays
          reachable from the tab bar and Home regardless. */}
      {!isFinished && (
      <div className="flex justify-around">
        <button
          className="flex flex-col items-center gap-1 text-xs text-text-secondary"
          onClick={() => mockAction('Share Live location', 'the map above is live for you, but a shareable trip link is not available yet')}
        >
          <IconCircle tone="soft" icon={<Radio />} />
          Share Live
        </button>
        <button
          className="flex flex-col items-center gap-1 text-xs text-danger"
          onClick={() => navigate('/sos', { state: { bookingId } })}
        >
          <IconCircle color="red" tone="soft" icon={<ShieldAlert />} />
          SOS
        </button>
        <button
          className="flex flex-col items-center gap-1 text-xs text-text-secondary"
          onClick={() => mockAction('Call driver', "your partner's number is not shared here yet")}
        >
          <IconCircle tone="soft" icon={<Phone />} />
          Call
        </button>
      </div>
      )}

      {canCancel && (
        <Button variant="danger" fullWidth disabled={cancelling} onClick={handleCancel}>
          {cancelling ? 'Cancelling...' : 'Cancel Ride'}
        </Button>
      )}
    </div>
  );
}

/** Whole seconds since an ISO timestamp, floored at 0 for clock skew. */
function secondsAgo(iso: string): number {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
}

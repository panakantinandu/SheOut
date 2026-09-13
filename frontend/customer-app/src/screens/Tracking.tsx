import { CheckCircle2, Headphones, MessageCircle, Radio, ShieldAlert, Star, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  AggregateRatingText,
  AmountText,
  Button,
  CancelReasonDialog,
  Card,
  CUSTOMER_CANCELLATION_REASONS,
  IconCircle,
  LiveMap,
  StatusBadge,
  TopHeader,
  bookingStatusLabel,
} from '@sheout/design-system';
import type { CancellationReason as SharedCancellationReason, MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi, chatApi, dispatchApi, ratingsApi } from '../api/client';
import type { AggregateRating, BookingSummary, DriverLocation } from '../api/types';
import { RatingPrompt } from '../components/RatingPrompt';
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
 * <p>
 * NO PHONE NUMBERS. The Call and Message buttons here were both mock
 * dialogs saying a partner's number was not shared "yet", which read as a
 * promise that one day it would be. It will not. A partner's number is
 * never shown to a rider and a rider's is never shown to a partner: it
 * cannot be taken back once given, and it outlives the trip it was given
 * for. Everything routine goes through booking-scoped chat; anything that
 * genuinely needs a voice goes to a person at SheOut on the support number,
 * who can hear both sides.
 */
export function Tracking() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [askingWhy, setAskingWhy] = useState(false);
  const [driverLocation, setDriverLocation] = useState<DriverLocation | null>(null);
  // Fetched from the chat endpoint, which serves it alongside the thread.
  // Null keeps the support button off the screen rather than offering a
  // button that dials nothing.
  const [supportPhoneNumber, setSupportPhoneNumber] = useState<string | null>(null);
  const [driverRating, setDriverRating] = useState<AggregateRating | null>(null);

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

  // One call, not a poll: the support number does not change while a trip
  // is running, and this is the endpoint that already knows it.
  useEffect(() => {
    if (!bookingId) return;
    let cancelled = false;
    chatApi
      .getThread(bookingId)
      .then((thread) => {
        if (!cancelled) setSupportPhoneNumber(thread.supportPhoneNumber || null);
      })
      .catch(() => {
        // Not worth surfacing. The support button simply stays hidden, and
        // SOS - which is the thing that actually matters in an emergency -
        // does not depend on this at all.
      });
    return () => {
      cancelled = true;
    };
  }, [bookingId]);

  // Fetched once a partner is assigned. Her score does not move during a
  // trip, so there is nothing to poll for.
  useEffect(() => {
    const driverId = booking?.driverId;
    if (!driverId) return;
    let cancelled = false;
    ratingsApi
      .forAccount(driverId)
      .then((rating) => {
        if (!cancelled) setDriverRating(rating);
      })
      .catch(() => {
        // The line falls back to "No ratings yet", which is also what a
        // genuinely unrated partner shows. Nothing here is worth an error.
      });
    return () => {
      cancelled = true;
    };
  }, [booking?.driverId]);

  /**
   * Cancels, with the reason the dialog collected.
   * <p>
   * The reason is required by the backend, so there is no path from this
   * screen that cancels without one. That is the point: a cancellation with
   * no reason cannot be told apart from any other, and an account later
   * flagged for cancelling too often deserves to have its side of it on
   * record.
   */
  async function handleCancel(reason: SharedCancellationReason, note?: string) {
    if (!bookingId) return;
    setCancelling(true);
    setCancelError(null);
    try {
      await bookingApi.cancel(bookingId, reason, note);
      setAskingWhy(false);
      navigate('/home', { replace: true });
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : 'Could not cancel booking');
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
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="md" variant="secondary" onClick={() => navigate('/home')}>
                {booking.status === 'CANCELLED' ? 'Book another ride' : 'Back to home'}
              </Button>
              {/* The thread is read-only now, but it is not gone. If there is
                  ever a disagreement about what was agreed on this trip, it
                  is the only account of it either side has - so it stays
                  reachable after the trip, not only during it. */}
              {booking.driverId && (
                <Button size="md" variant="secondary" onClick={() => navigate(`/chat/${booking.id}`)}>
                  View messages
                </Button>
              )}
            </div>
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
            {/* Name, photo and vehicle are still MOCK - see the file header.
                The rating is not: it is her real average, from what riders
                submitted after her completed trips. */}
            <p className="font-heading font-semibold text-text-primary">Driver details unavailable (mock)</p>
            <p className="text-xs text-text-secondary">
              <AggregateRatingText
                averageStars={driverRating?.averageStars}
                totalRatings={driverRating?.totalRatings}
                emptyLabel="No ratings yet"
              />{' '}
              &middot; Bike (mock) &middot; Reg. unavailable (mock)
            </p>
          </div>
          {/* The one way to reach her. Not a call, and not a number. */}
          <button
            aria-label="Message your partner"
            className="rounded-full p-2 text-primary hover:bg-background"
            onClick={() => navigate(`/chat/${bookingId}`)}
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
          cancelled trip, or offering to message a driver who was never
          coming, are both offers of something that does not exist. SOS stays
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
        {/* Was "Call", and it dialled nothing. It now reaches a person at
            SheOut rather than the partner - which is what a rider wanting to
            call about a trip actually needs, and the only call this product
            will ever place between the two of them. Hidden entirely when no
            support number is configured. */}
        {supportPhoneNumber ? (
          <button
            className="flex flex-col items-center gap-1 text-xs text-text-secondary"
            onClick={() => { window.location.href = `tel:${supportPhoneNumber}`; }}
          >
            <IconCircle tone="soft" icon={<Headphones />} />
            Support
          </button>
        ) : (
          <button
            className="flex flex-col items-center gap-1 text-xs text-text-secondary"
            onClick={() => navigate(`/chat/${bookingId}`)}
          >
            <IconCircle tone="soft" icon={<MessageCircle />} />
            Chat
          </button>
        )}
      </div>
      )}

      {canCancel && (
        <Button variant="danger" fullWidth disabled={cancelling} onClick={() => { setCancelError(null); setAskingWhy(true); }}>
          Cancel Ride
        </Button>
      )}

      <CancelReasonDialog
        open={askingWhy}
        options={CUSTOMER_CANCELLATION_REASONS}
        busy={cancelling}
        error={cancelError}
        onConfirm={handleCancel}
        onCancel={() => setAskingWhy(false)}
      />

      {/* Asked about this trip specifically, and only once it has actually
          completed. A cancelled trip is never rated - there is nothing to
          say about a ride that did not happen, and asking would read as
          blaming somebody for it. The server agrees: no slot is opened for a
          cancellation. */}
      {booking?.status === 'COMPLETED' && (
        <RatingPrompt bookingId={bookingId} counterpartLabel="your partner" />
      )}
    </div>
  );
}

/** Whole seconds since an ISO timestamp, floored at 0 for clock skew. */
function secondsAgo(iso: string): number {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
}

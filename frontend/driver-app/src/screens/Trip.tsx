import { CheckCircle2, MapPin, MessageCircle, Navigation } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  CancelReasonDialog,
  Card,
  ContactSupportButton,
  DRIVER_CANCELLATION_REASONS,
  IconCircle,
  LiveMap,
  OpenInMapsButton,
  PICKUP_CODE_LENGTH,
  PickupCodeField,
  StatusBadge,
  TopHeader,
  bookingStatusLabel,
} from '@sheout/design-system';
import type { CancellationReason, MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi, chatApi } from '../api/client';
import type { BookingSummary, TripRoute } from '../api/types';
import { CollectPaymentCard } from '../components/CollectPaymentCard';
import { useShareLocation } from '../lib/LocationBroadcastContext';

const POLL_INTERVAL_MS = 4000;

/**
 * How far she has to drift from the point the route was drawn for before it
 * is worth drawing again.
 * <p>
 * Three hundred metres, and the number is a rate-limit decision, not a
 * cartographic one. Re-routing on every position update would mean roughly
 * fifty OSRM calls per trip against a volunteer-run demo instance; this
 * makes it about two, plus one if she takes a different road than the line
 * suggested. The drawn line is orientation - the real turn-by-turn is in
 * Google Maps, one tap away, and it reroutes properly.
 */
const ROUTE_REFRESH_METRES = 300;

const EARTH_RADIUS_M = 6371000;

function metresBetween(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2
    + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
}

/**
 * The active trip, in its two phases.
 * <p>
 * PHASE ONE - ACCEPTED. She is going to the rider. The map draws the road
 * from where she is to the PICKUP, and the Google Maps button navigates
 * there. The drop is not the job yet, and showing a route to it would send
 * her to the wrong end of the trip.
 * <p>
 * PHASE TWO - IN_PROGRESS. The rider is in the vehicle. The map and the
 * button both switch to the DROP.
 * <p>
 * BETWEEN THEM IS THE PICKUP CODE, AND THAT IS THE POINT OF THIS SCREEN.
 * "Start Trip" used to be an unverified tap: a partner could move a booking
 * to IN_PROGRESS and then COMPLETED with nobody in the vehicle, and the
 * rider was charged the fare. Now the rider reads four digits off her own
 * screen, the partner types them, and the server checks them. Which phase
 * the screen is in is read from the booking's status, never from local
 * state, so a refresh, a second device or a backgrounded app all agree.
 * <p>
 * REAL: booking status/pickup/drop/fare, polled from GET /bookings/{id}.
 * The route comes from GET /bookings/{id}/route, which picks its own
 * destination from that same status. "You" is this device's own GPS.
 * <p>
 * MOCK: customer name - there is still no driver-facing endpoint to look up
 * a rider's profile by id.
 * <p>
 * NO PHONE NUMBERS. Everything routine goes through booking-scoped chat;
 * anything needing a voice goes to a person at SheOut on the support number.
 */
export function Trip() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [askingWhy, setAskingWhy] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [supportPhoneNumber, setSupportPhoneNumber] = useState<string | null>(null);

  const [pickupCode, setPickupCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);
  /** Set once the server says the attempt limit is spent. The keypad closes. */
  const [codeLocked, setCodeLocked] = useState(false);

  const [route, setRoute] = useState<TripRoute | null>(null);
  const [routeError, setRouteError] = useState(false);
  // Where she was when the current line was drawn, so the next fix can be
  // measured against it rather than re-routing on every one.
  const routedFrom = useRef<{ lat: number; lng: number } | null>(null);

  // Live for the whole trip, MATCHED included. The subscription itself
  // lives above the router so it is not dropped on the way in from Home or
  // the offer screen - see LocationBroadcastContext.
  const tripIsLive = booking
    ? ['MATCHED', 'ACCEPTED', 'IN_PROGRESS'].includes(booking.status)
    : false;
  const location = useShareLocation(tripIsLive);
  const myPosition = location.position;

  const phase = booking?.status === 'IN_PROGRESS' ? 'DROP' : 'PICKUP';

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

  /**
   * Draws the road to wherever this phase is going.
   * <p>
   * Fetched when the phase changes, and again only once she has moved well
   * off the point it was drawn from. The server decides the destination
   * from the booking's status; this never asks for one.
   */
  const loadRoute = useCallback(
    async (from: { lat: number; lng: number }) => {
      if (!bookingId) return;
      try {
        const fetched = await bookingApi.getRoute(bookingId, from);
        setRoute(fetched);
        setRouteError(!fetched.points.length);
        routedFrom.current = from;
      } catch {
        // A route is a convenience, not the trip. The destination marker and
        // the Google Maps button both still work without it, so this is
        // reported on the map caption rather than as a screen error.
        setRouteError(true);
      }
    },
    [bookingId]
  );

  // Re-route on a phase change, and drop the old line immediately so a route
  // to the pickup is never left on screen after the trip has started.
  useEffect(() => {
    setRoute(null);
    setRouteError(false);
    routedFrom.current = null;
  }, [phase, bookingId]);

  useEffect(() => {
    if (!myPosition || !booking) return;
    if (booking.status !== 'ACCEPTED' && booking.status !== 'IN_PROGRESS') return;
    const last = routedFrom.current;
    if (last && metresBetween(last, myPosition) < ROUTE_REFRESH_METRES) return;
    loadRoute(myPosition);
  }, [myPosition, booking, loadRoute]);

  // One call, not a poll: the support number does not change mid-trip.
  useEffect(() => {
    if (!bookingId) return;
    let cancelled = false;
    chatApi
      .getThread(bookingId)
      .then((thread) => {
        if (!cancelled) setSupportPhoneNumber(thread.supportPhoneNumber || null);
      })
      .catch(() => {
        // Not worth surfacing - the button simply stays hidden.
      });
    return () => {
      cancelled = true;
    };
  }, [bookingId]);

  /**
   * Cancels, with the reason the dialog collected.
   * <p>
   * A partner's cancellations are counted the same way a rider's are, and
   * for the same reason: an account that walks away from trips it took on
   * is a real cost to whoever was waiting.
   */
  async function handleCancel(reason: CancellationReason, note?: string) {
    if (!bookingId) return;
    setBusy(true);
    setCancelError(null);
    try {
      const updated = await bookingApi.cancel(bookingId, reason, note);
      setBooking(updated);
      setAskingWhy(false);
      navigate('/home', { replace: true });
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : 'Could not cancel trip');
    } finally {
      setBusy(false);
    }
  }

  /**
   * Confirms the pickup with the code the rider read out.
   * <p>
   * A wrong code and a lockout are told apart on the machine-readable code
   * the server sends, not on the message text. One leaves her the keypad to
   * try again; the other takes it away and points her at support, because
   * five more attempts she cannot make is not useful information at a kerb.
   */
  async function handleConfirmPickup() {
    if (!bookingId || pickupCode.length !== PICKUP_CODE_LENGTH) return;
    setBusy(true);
    setCodeError(null);
    try {
      const updated = await bookingApi.start(bookingId, pickupCode);
      setBooking(updated);
      setPickupCode('');
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'PICKUP_VERIFICATION_LOCKED') {
        setCodeLocked(true);
        setCodeError(err.message);
      } else {
        setCodeError(err instanceof ApiError ? err.message : 'Could not confirm pickup');
        // Cleared so she types four fresh digits rather than editing a
        // wrong code - which is how a second attempt becomes a third.
        setPickupCode('');
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleComplete() {
    if (!bookingId) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await bookingApi.complete(bookingId);
      // Stays on this screen: the fare still has to be collected, and the
      // payment card below appears in place of the trip controls.
      setBooking(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not complete trip');
    } finally {
      setBusy(false);
    }
  }

  /**
   * Only the leg she is on, plus where she is.
   * <p>
   * Both ends of the trip used to be drawn at all times, which meant the map
   * auto-fitted to show a drop she was not going to yet and zoomed out past
   * the point where a pickup on a side street was findable.
   */
  const markers: MapMarker[] = [];
  if (booking) {
    if (phase === 'PICKUP') {
      markers.push({ key: 'pickup', lat: booking.pickup.lat, lng: booking.pickup.lng, label: 'Pickup', kind: 'pickup' });
    } else {
      markers.push({ key: 'drop', lat: booking.drop.lat, lng: booking.drop.lng, label: 'Drop', kind: 'drop' });
    }
  }
  if (myPosition) {
    markers.push({ key: 'me', lat: myPosition.lat, lng: myPosition.lng, label: 'You', kind: 'driver' });
  }

  const destination = booking ? (phase === 'PICKUP' ? booking.pickup : booking.drop) : null;
  const navigable = booking?.status === 'ACCEPTED' || booking?.status === 'IN_PROGRESS';

  function mapCaption(): string {
    if (!navigable) return 'Your position updates as your device reports movement.';
    if (routeError) return 'Could not draw the road right now - open Google Maps for directions.';
    if (route?.distanceKm != null) {
      const minutes = route.durationMinutes == null ? null : Math.round(route.durationMinutes);
      return `${route.distanceKm} km${minutes == null ? '' : ` - about ${minutes} min`} to the ${
        phase === 'PICKUP' ? 'pickup' : 'drop'
      }.`;
    }
    if (!myPosition) return location.error ?? 'Finding your location...';
    return 'Working out the route...';
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Trip" onBack={() => navigate('/home')} />

      <div className="space-y-1">
        <LiveMap markers={markers} route={route?.points} />
        <p className="text-xs text-text-secondary">{mapCaption()}</p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!booking && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {booking && (
        <>
          {/* Once the trip is over, getting paid is the job in front of her. */}
          {booking.status === 'COMPLETED' && bookingId && <CollectPaymentCard bookingId={bookingId} />}

          {/* Where she is going NEXT, on its own and stated first. The
              two-address list below is the whole trip; this is the job in
              front of her. */}
          {navigable && destination && (
            <Card tone={phase === 'PICKUP' ? 'brand' : 'default'} className="space-y-3">
              <div className="flex items-start gap-3">
                <IconCircle
                  tone="soft"
                  color={phase === 'PICKUP' ? undefined : 'orange'}
                  icon={phase === 'PICKUP' ? <MapPin /> : <Navigation />}
                />
                <div className="min-w-0 flex-1">
                  <p className="font-heading font-semibold text-text-primary">
                    {phase === 'PICKUP' ? 'Go to pickup' : 'Go to drop'}
                  </p>
                  <p className="mt-0.5 text-sm text-text-secondary">{destination.label}</p>
                </div>
              </div>
              <OpenInMapsButton
                lat={destination.lat}
                lng={destination.lng}
                label={destination.label}
                variant={phase === 'PICKUP' ? 'primary' : 'secondary'}
              >
                {phase === 'PICKUP' ? 'Navigate to pickup' : 'Navigate to drop'}
              </OpenInMapsButton>
            </Card>
          )}

          {/* The gate between the two phases. */}
          {booking.status === 'ACCEPTED' && (
            <Card className="space-y-3">
              <div className="flex items-start gap-3">
                <IconCircle tone="soft" icon={<CheckCircle2 />} />
                <div className="min-w-0 flex-1">
                  <p className="font-heading font-semibold text-text-primary">Confirm pickup</p>
                  <p className="mt-0.5 text-sm text-text-secondary">
                    {codeLocked
                      ? 'This trip needs support to sort out before it can start.'
                      : 'Ask your rider for her four-digit code and enter it here. The trip starts once it matches.'}
                  </p>
                </div>
              </div>
              {!codeLocked && (
                <>
                  <PickupCodeField
                    value={pickupCode}
                    onChange={(v) => {
                      setPickupCode(v);
                      setCodeError(null);
                    }}
                    error={codeError ?? undefined}
                    disabled={busy}
                    onSubmit={handleConfirmPickup}
                  />
                  <Button
                    fullWidth
                    disabled={busy || pickupCode.length !== PICKUP_CODE_LENGTH}
                    onClick={handleConfirmPickup}
                  >
                    {busy ? 'Checking...' : 'Confirm Pickup & Start Trip'}
                  </Button>
                </>
              )}
              {codeLocked && codeError && <p className="text-sm text-danger">{codeError}</p>}
            </Card>
          )}

          <Card className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="font-heading font-semibold text-text-primary">{booking.type === 'RIDE' ? 'Ride' : 'Delivery'}</p>
              <StatusBadge tone="primary">{bookingStatusLabel(booking.status)}</StatusBadge>
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
              <p className="text-xs text-text-secondary">Phone numbers are never shared. Message her instead.</p>
            </div>
            <button
              aria-label="Message your rider"
              className="rounded-full p-2 text-primary hover:bg-background"
              onClick={() => navigate(`/chat/${bookingId}`)}
            >
              <MessageCircle className="h-5 w-5" />
            </button>
          </Card>

          <div className="space-y-3">
            {booking.status === 'COMPLETED' && (
              <Button fullWidth variant="secondary" onClick={() => navigate('/home', { replace: true })}>
                Done
              </Button>
            )}
            {booking.status === 'IN_PROGRESS' && (
              <Button fullWidth variant="success" disabled={busy} onClick={handleComplete}>
                {busy ? 'Completing...' : 'Complete Trip'}
              </Button>
            )}
            {(booking.status === 'MATCHED' || booking.status === 'ACCEPTED') && (
              <Button
                fullWidth
                variant="danger"
                disabled={busy}
                onClick={() => { setCancelError(null); setAskingWhy(true); }}
              >
                Cancel Trip
              </Button>
            )}
            <ContactSupportButton phoneNumber={supportPhoneNumber} />
          </div>
        </>
      )}

      <CancelReasonDialog
        open={askingWhy}
        options={DRIVER_CANCELLATION_REASONS}
        busy={busy}
        error={cancelError}
        onConfirm={handleCancel}
        onCancel={() => setAskingWhy(false)}
      />
    </div>
  );
}

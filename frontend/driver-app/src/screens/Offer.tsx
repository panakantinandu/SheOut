import { Clock, Navigation } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AmountText, Button, Card, LiveMap, TopHeader } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi, dispatchApi } from '../api/client';
import type { OfferSummary } from '../api/types';

const POLL_INTERVAL_MS = 3000;
// Matches sheout.dispatch.offer-window-seconds's default (see render.yaml /
// application.yml) - GET /dispatch/offers/me returns no expiresAt, so this
// countdown is a client-side approximation starting from when this screen
// opened, not a value the backend actually hands over.
const OFFER_WINDOW_SECONDS = 15;
const EARTH_RADIUS_KM = 6371;

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/** Straight-line (haversine) distance, not a routed distance - no routing/directions API is configured anywhere in this project. */
function distanceKm(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
}

/**
 * REAL: pickup/drop/fare come straight from GET /dispatch/offers/me's own
 * (enriched) response, NOT a separate GET /bookings/{id} call - that call
 * doesn't work here: a driver who's only been OFFERED this booking, not
 * yet accepted it, isn't a participant on it yet, so BookingController's
 * requireParticipant correctly 404s them (found the hard way - this
 * screen's original version called it and every real offer failed to
 * load). Accept calls the real dispatch-accept + booking-accept pair (see
 * Home.tsx's previous comment on why two calls); Decline calls the real
 * decline endpoint. Distance is a real haversine calculation from the
 * offer's own real pickup/drop coordinates - straight-line, not routed,
 * since no routing API is configured anywhere in this project.
 * <p>
 * "No longer available": this screen keeps polling GET /dispatch/offers/me
 * while open - if this bookingId stops coming back (offer expired
 * server-side, or another driver won the race), or Accept itself 409s
 * (OFFER_NOT_FOUND / DRIVER_NO_LONGER_ELIGIBLE / BOOKING_ALREADY_ASSIGNED /
 * ASSIGNMENT_FAILED - see DispatchController), this shows a clear
 * unavailable state instead of a silent failure or a stuck screen.
 */
export function Offer() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [offer, setOffer] = useState<OfferSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const [responding, setResponding] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(OFFER_WINDOW_SECONDS);
  const seenAtRef = useRef(Date.now());

  // Fetches the offer (with its enriched booking details) and keeps
  // re-confirming it's still ours to answer, on the same poll.
  useEffect(() => {
    if (!bookingId || unavailable) return;
    let cancelled = false;
    async function poll() {
      try {
        const current = await dispatchApi.getMyOffer();
        if (cancelled) return;
        if (!current || current.bookingId !== bookingId) {
          setUnavailable(true);
          return;
        }
        setOffer(current);
      } catch {
        if (!cancelled) setError('Could not load this request');
      }
    }
    poll();
    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [bookingId, unavailable]);

  useEffect(() => {
    const tick = setInterval(() => {
      const elapsed = Math.floor((Date.now() - seenAtRef.current) / 1000);
      const remaining = Math.max(0, OFFER_WINDOW_SECONDS - elapsed);
      setSecondsLeft(remaining);
      if (remaining === 0) setUnavailable(true);
    }, 1000);
    return () => clearInterval(tick);
  }, []);

  async function handleAccept() {
    if (!bookingId) return;
    setResponding(true);
    setError(null);
    try {
      await dispatchApi.acceptOffer(bookingId);
      await bookingApi.accept(bookingId);
      navigate(`/trip/${bookingId}`, { replace: true });
    } catch (err) {
      setUnavailable(true);
      setError(err instanceof ApiError ? err.message : 'Could not accept - it may no longer be available');
    } finally {
      setResponding(false);
    }
  }

  async function handleDecline() {
    if (!bookingId) return;
    setResponding(true);
    try {
      await dispatchApi.declineOffer(bookingId);
    } catch {
      // May have already expired server-side - navigating home either way is correct.
    } finally {
      navigate('/home', { replace: true });
    }
  }

  const distance = offer?.pickup && offer?.drop ? distanceKm(offer.pickup, offer.drop) : null;

  // Where the job actually is. A fare and two place names are not enough to
  // judge an offer in fifteen seconds - every real driver app shows the
  // pickup on a map, and the mockup's New Request tile does too.
  const markers: MapMarker[] = [];
  if (offer?.pickup) markers.push({ key: 'pickup', lat: offer.pickup.lat, lng: offer.pickup.lng, label: 'Pickup', kind: 'pickup' });
  if (offer?.drop) markers.push({ key: 'drop', lat: offer.drop.lat, lng: offer.drop.lng, label: 'Drop', kind: 'drop' });

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="New Request" onBack={() => navigate('/home')} />

      {!offer && !error && !unavailable && <p className="text-center text-sm text-text-secondary">Loading...</p>}
      {error && <p className="text-sm text-danger">{error}</p>}

      {offer && !unavailable && (
        <>
          {markers.length > 0 && <LiveMap markers={markers} className="h-52" />}

          <Card className="flex items-center justify-between bg-primary-light">
            <div className="flex items-center gap-2 text-primary">
              <Clock className="h-4 w-4" />
              <span className="text-sm font-semibold">Respond within {secondsLeft}s</span>
            </div>
            {offer.fareEstimate != null && <AmountText amount={offer.fareEstimate} size="lg" />}
          </Card>

          <Card className="space-y-3">
            {offer.pickup && (
              <div className="flex items-start gap-2">
                <Navigation className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                <span className="min-w-0">
                  <span className="block text-xs text-text-secondary">Pickup</span>
                  <span className="block text-sm text-text-primary">{offer.pickup.label}</span>
                </span>
              </div>
            )}
            {offer.drop && (
              <div className="flex items-start gap-2">
                <Navigation className="mt-0.5 h-4 w-4 shrink-0 text-accent-orange" />
                <span className="min-w-0">
                  <span className="block text-xs text-text-secondary">Drop</span>
                  <span className="block text-sm text-text-primary">{offer.drop.label}</span>
                </span>
              </div>
            )}
            {distance != null && (
              <div className="flex justify-between border-t border-border pt-3 text-sm">
                <span className="text-text-secondary">Distance</span>
                <span className="font-medium text-text-primary">{distance.toFixed(1)} km</span>
              </div>
            )}
          </Card>

          <div className="flex gap-3">
            <Button variant="secondary" fullWidth disabled={responding} onClick={handleDecline}>
              Decline
            </Button>
            <Button variant="success" fullWidth disabled={responding} onClick={handleAccept}>
              {responding ? 'Accepting...' : 'Accept'}
            </Button>
          </div>
        </>
      )}

      {unavailable && (
        <Card className="space-y-3 text-center">
          <p className="font-heading font-semibold text-text-primary">This request is no longer available</p>
          <p className="text-sm text-text-secondary">It expired or another driver accepted it first.</p>
          <Button fullWidth onClick={() => navigate('/home', { replace: true })}>
            Back to Home
          </Button>
        </Card>
      )}
    </div>
  );
}

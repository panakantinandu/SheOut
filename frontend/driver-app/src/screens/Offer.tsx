import { Navigation } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AmountText, Button, Card, CountdownRing, LiveAnnouncement, LiveMap, SkeletonCard, StickyActions, TopHeader, formatRupees, useRouteLine } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi, dispatchApi, routesApi } from '../api/client';
import { apiErrorText } from '../lib/apiErrors';
import type { OfferSummary } from '../api/types';
import { useShareLocation } from '../lib/LocationBroadcastContext';
import { useTranslation } from '@sheout/design-system';

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

/** Straight-line (haversine) distance: the fallback when the road route cannot be had (see routedKm). */
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
  const { t } = useTranslation();
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [offer, setOffer] = useState<OfferSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const [responding, setResponding] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(OFFER_WINDOW_SECONDS);
  // A screen reader hears the offer once, the moment it arrives - what she
  // earns, where, how long she has - and focus moves to its details, not to
  // Accept, so a stray Enter never takes a trip. Frozen at arrival: the
  // seconds ticking down must not be read out every second.
  const [announcement, setAnnouncement] = useState('');
  const details = useRef<HTMLDivElement>(null);
  const seenAtRef = useRef(Date.now());

  // Keep sharing position while this screen is open. This screen is what a
  // partner is looking at during MATCHED, and it previously shared nothing:
  // the whole time the rider was being told a partner had been found, the
  // driver marker on the rider's map sat at whatever position was last sent
  // from Home. Retaining here closes that gap. See LocationBroadcastContext.
  useShareLocation(true);

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
        if (!cancelled) setError(t('offer.loadError'));
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

  // Opened from an old "New trip request" notification, the offer is long
  // gone - but the trip may be hers. The server only shows a booking to its
  // assigned partner, so if it answers, she took this one: show her the trip
  // rather than a dead end. If it refuses, the request went to somebody else
  // or nobody, and the closed card below says so.
  useEffect(() => {
    if (!unavailable || !bookingId || responding) return;
    let cancelled = false;
    bookingApi
      .getById(bookingId)
      .then((b) => {
        if (!cancelled && b.driverId) navigate(`/trip/${bookingId}`, { replace: true });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [unavailable, bookingId, responding, navigate]);

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
      setError(apiErrorText(err, 'offer.acceptError'));
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

  // The road distance, the same one the customer was quoted from; straight
  // line only if routing is down (it reads shorter - 5.9 km against a 7.9 km
  // quote - so it is never preferred).
  const [routedKm, setRoutedKm] = useState<number | null>(null);
  useEffect(() => {
    if (!offer?.pickup || !offer?.drop) return;
    let cancelled = false;
    routesApi
      .preview(offer.pickup, offer.drop)
      .then((r) => {
        if (!cancelled) setRoutedKm(r.distanceKm);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [offer?.pickup?.lat, offer?.pickup?.lng, offer?.drop?.lat, offer?.drop?.lng]);
  const distance = routedKm ?? (offer?.pickup && offer?.drop ? distanceKm(offer.pickup, offer.drop) : null);

  // Where the job actually is. A fare and two place names are not enough to
  // judge an offer in fifteen seconds - every real driver app shows the
  // pickup on a map, and the mockup's New Request tile does too.
  // The trip she is being offered, pickup to drop, as a road.
  const route = useRouteLine(routesApi.preview, offer?.pickup, offer?.drop);
  const markers: MapMarker[] = [];
  if (offer?.pickup) markers.push({ key: 'pickup', lat: offer.pickup.lat, lng: offer.pickup.lng, label: t('trip.pickup'), kind: 'pickup' });
  if (offer?.drop) markers.push({ key: 'drop', lat: offer.drop.lat, lng: offer.drop.lng, label: t('trip.drop'), kind: 'drop' });

  useEffect(() => {
    if (!offer || announcement) return;
    setAnnouncement(
      t('offer.announce', {
        amount: formatRupees(offer.partnerEarnings ?? offer.fareEstimate ?? 0),
        pickup: offer.pickup?.label ?? '',
        seconds: secondsLeft,
      })
    );
    details.current?.focus();
    // Once per offer; the seconds are read at arrival only.
  }, [offer]);

  return (
    <div className="space-y-6">
      <LiveAnnouncement urgent message={secondsLeft > 0 ? announcement : t('offer.announceTimeUp')} testId="offer-announcement" />
      <TopHeader variant="back" title={t('offer.title')} onBack={() => navigate('/home')} />

      {!offer && !error && !unavailable && <SkeletonCard lines={3} label={t('offer.loading')} />}
      {error && <p className="text-sm text-danger">{error}</p>}

      {offer && !unavailable && (
        <div ref={details} tabIndex={-1} className="space-y-6 outline-none" data-testid="offer-details">
          {/* Sized to the screen: on a short phone the trip details (distance
              included) stay above the Accept bar. */}
          {markers.length > 0 && <LiveMap markers={markers} route={route} className="h-[22vh] max-h-52 min-h-32" />}

          {/* The clock, as a ring that empties. This is the tensest moment
              in the whole app - a fare, two place names, fifteen seconds -
              and a number ticking down has to be read and subtracted from.
              A ring is answered at a glance, which is all the attention
              there is to spare here. The fare is NOT animated: a price that
              moves while she decides is a price she cannot trust. */}
          <Card tone="brand" className="flex items-center justify-between">
            <div className="flex items-center gap-3 text-primary">
              <CountdownRing secondsLeft={secondsLeft} totalSeconds={OFFER_WINDOW_SECONDS} />
              <span className="text-sm font-semibold">
                {secondsLeft > 0 ? t('offer.secondsToRespond') : t('offer.timeUp')}
              </span>
            </div>
            {/* Her earnings first, as she will be paid them; the fare and
                SheOut's share under it. An older server sends only the fare. */}
            {offer.partnerEarnings != null ? (
              <div className="text-right" data-testid="offer-earnings">
                <p className="text-xs font-medium text-text-secondary">{t('offer.youEarn')}</p>
                <AmountText amount={offer.partnerEarnings} exact size="lg" />
              </div>
            ) : (
              offer.fareEstimate != null && <AmountText amount={offer.fareEstimate} size="lg" />
            )}
          </Card>
          {offer.partnerEarnings != null && offer.fareEstimate != null && (
            <p className="-mt-2 px-1 text-right text-xs text-text-secondary" data-testid="offer-fare-line">
              {t('offer.fareAndFee', { fare: formatRupees(offer.fareEstimate, true), percent: offer.commissionPercent ?? '-' })}
            </p>
          )}

          <Card className="space-y-3">
            {offer.pickup && (
              <div className="flex items-start gap-2">
                <Navigation className="mt-1 h-4 w-4 shrink-0 text-primary" />
                <span className="min-w-0">
                  <span className="block text-xs text-text-secondary">{t('trip.pickup')}</span>
                  <span className="block text-sm text-text-primary">{offer.pickup.label}</span>
                </span>
              </div>
            )}
            {offer.drop && (
              <div className="flex items-start gap-2">
                <Navigation className="mt-1 h-4 w-4 shrink-0 text-accent-orange" />
                <span className="min-w-0">
                  <span className="block text-xs text-text-secondary">{t('trip.drop')}</span>
                  <span className="block text-sm text-text-primary">{offer.drop.label}</span>
                </span>
              </div>
            )}
            {distance != null && (
              <div className="flex justify-between border-t border-border pt-3 text-sm">
                <span className="text-text-secondary">{t('offer.distance')}</span>
                <span className="font-medium text-text-primary">{distance.toFixed(1)} km</span>
              </div>
            )}
          </Card>

          {/* Always in view: she has fifteen seconds, and on a 640 px phone
              these sat under the map and the trip card - she had to scroll
              to reach Accept before the request ran out. */}
          <StickyActions testId="offer-actions">
            <div className="flex gap-3">
              <Button variant="secondary" fullWidth disabled={responding} onClick={handleDecline}>
                {t('offer.decline')}
              </Button>
              <Button variant="success" fullWidth disabled={responding} onClick={handleAccept} data-testid="offer-accept">
                {responding ? t('offer.accepting') : t('offer.accept')}
              </Button>
            </div>
          </StickyActions>
        </div>
      )}

      {unavailable && (
        <Card className="space-y-3 text-center">
          <p className="font-heading text-card-title text-text-primary">{t('offer.unavailableTitle')}</p>
          <p className="text-sm text-text-secondary">{t('offer.unavailableBody')}</p>
          <Button fullWidth onClick={() => navigate('/home', { replace: true })}>
            {t('offer.backHome')}
          </Button>
          <Button fullWidth variant="secondary" onClick={() => navigate('/bookings', { replace: true })}>
            {t('offer.myTrips')}
          </Button>
        </Card>
      )}
    </div>
  );
}

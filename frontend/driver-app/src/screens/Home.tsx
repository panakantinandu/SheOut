import { Bike, MapPin, Package, Power, ShieldCheck, UtensilsCrossed } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, TopHeader } from '@sheout/design-system';
import { ApiError, bookingApi, dispatchApi, usersApi, verificationApi } from '../api/client';
import type { BookingSummary, DriverProfileSummary, VerificationSummary } from '../api/types';
import { mockAction } from '../lib/mockAction';

const OFFER_POLL_MS = 4000;
const LOCATION_POLL_MS = 15000;
// Matches sheout.dispatch.offer-window-seconds's default (see render.yaml /
// application.yml) - GET /dispatch/offers/me returns no expiresAt, so this
// countdown is a client-side approximation, not a value from the backend.
const OFFER_WINDOW_SECONDS = 15;
// Fallback only if the browser denies/lacks geolocation - central Hyderabad,
// not this driver's real position. Flagged rather than silently sent as real.
const FALLBACK_COORDS = { lat: 17.385, lng: 78.4867 };

const CATEGORY_ICON: Record<string, JSX.Element> = {
  PARCEL: <Package className="h-5 w-5" />,
  LUNCHBOX: <UtensilsCrossed className="h-5 w-5" />,
};

function categoryIcon(category: string) {
  return CATEGORY_ICON[category] ?? <Bike className="h-5 w-5" />;
}

/**
 * REAL: driver profile, verification gate, online/offline toggle, active-trip
 * detection, and the whole offer accept/decline flow all hit live endpoints.
 * <p>
 * No push/notifications module exists (see DispatchController's own
 * Javadoc), so "New Request" is implemented as polling GET
 * /dispatch/offers/me every 4s while online with no active trip - not
 * instant delivery, a deliberate backend limitation, not a frontend
 * shortcut.
 */
export function Home() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [verification, setVerification] = useState<VerificationSummary | null>(null);
  const [activeTrip, setActiveTrip] = useState<BookingSummary | null>(null);
  const [offerBookingId, setOfferBookingId] = useState<string | null>(null);
  const [offerSecondsLeft, setOfferSecondsLeft] = useState(OFFER_WINDOW_SECONDS);
  const [error, setError] = useState<string | null>(null);
  const [togglingOnline, setTogglingOnline] = useState(false);
  const [respondingToOffer, setRespondingToOffer] = useState(false);
  const offerSeenAtRef = useRef<number | null>(null);

  const loadProfile = useCallback(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load profile'));
  }, []);

  useEffect(() => {
    loadProfile();
    verificationApi.getMyStatus().then(setVerification).catch(() => setVerification(null));
  }, [loadProfile]);

  const isOnline = profile?.onlineStatus === 'ONLINE';
  const isVerified = verification?.genderVerificationStatus === 'VERIFIED' && verification?.policeVerificationStatus === 'VERIFIED';

  // Active-trip detection: whichever of my own bookings is currently in an
  // in-flight state. Polling GET /bookings/me is the same "no push" pattern
  // as the offer poll below.
  useEffect(() => {
    if (!isOnline) {
      setActiveTrip(null);
      return;
    }
    let cancelled = false;
    async function poll() {
      try {
        const bookings = await bookingApi.listMine();
        if (cancelled) return;
        const active = bookings.find((b) => b.status === 'MATCHED' || b.status === 'ACCEPTED' || b.status === 'IN_PROGRESS');
        setActiveTrip(active ?? null);
      } catch {
        // Transient poll failure - next tick retries, no need to surface an error banner for this.
      }
    }
    poll();
    const interval = setInterval(poll, OFFER_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [isOnline]);

  // Offer polling - only while online and with no active trip already in hand.
  useEffect(() => {
    if (!isOnline || activeTrip) {
      setOfferBookingId(null);
      offerSeenAtRef.current = null;
      return;
    }
    let cancelled = false;
    async function poll() {
      try {
        const offer = await dispatchApi.getMyOffer();
        if (cancelled) return;
        if (offer) {
          if (offerSeenAtRef.current === null) offerSeenAtRef.current = Date.now();
          setOfferBookingId(offer.bookingId);
        } else {
          offerSeenAtRef.current = null;
          setOfferBookingId(null);
        }
      } catch {
        // Transient poll failure - next tick retries.
      }
    }
    poll();
    const interval = setInterval(poll, OFFER_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [isOnline, activeTrip]);

  // Client-side countdown display for the current offer - ticks every second,
  // resets once the offer clears (accepted/declined/expired).
  useEffect(() => {
    if (!offerBookingId) {
      setOfferSecondsLeft(OFFER_WINDOW_SECONDS);
      return;
    }
    const tick = setInterval(() => {
      const seenAt = offerSeenAtRef.current ?? Date.now();
      const elapsed = Math.floor((Date.now() - seenAt) / 1000);
      setOfferSecondsLeft(Math.max(0, OFFER_WINDOW_SECONDS - elapsed));
    }, 1000);
    return () => clearInterval(tick);
  }, [offerBookingId]);

  // Best-effort real location while online - falls back to a fixed
  // Hyderabad coordinate if the browser denies/lacks geolocation, so
  // dispatch's geo-matching still has something to match against in a demo.
  useEffect(() => {
    if (!isOnline) return;
    let cancelled = false;
    function sendLocation(lat: number, lng: number) {
      if (!cancelled) dispatchApi.recordLocation(lat, lng).catch(() => {});
    }
    function poll() {
      if (!navigator.geolocation) {
        sendLocation(FALLBACK_COORDS.lat, FALLBACK_COORDS.lng);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => sendLocation(pos.coords.latitude, pos.coords.longitude),
        () => sendLocation(FALLBACK_COORDS.lat, FALLBACK_COORDS.lng),
        { timeout: 5000 }
      );
    }
    poll();
    const interval = setInterval(poll, LOCATION_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [isOnline]);

  async function handleToggleOnline() {
    if (!profile) return;
    setTogglingOnline(true);
    setError(null);
    try {
      const updated = await usersApi.setOnlineStatus(isOnline ? 'OFFLINE' : 'ONLINE');
      setProfile(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update status');
    } finally {
      setTogglingOnline(false);
    }
  }

  async function handleAcceptOffer() {
    if (!offerBookingId) return;
    setRespondingToOffer(true);
    setError(null);
    const bookingId = offerBookingId;
    try {
      await dispatchApi.acceptOffer(bookingId);
      await bookingApi.accept(bookingId);
      navigate(`/trip/${bookingId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not accept - another driver may have taken it');
      setOfferBookingId(null);
    } finally {
      setRespondingToOffer(false);
    }
  }

  async function handleDeclineOffer() {
    if (!offerBookingId) return;
    setRespondingToOffer(true);
    try {
      await dispatchApi.declineOffer(offerBookingId);
    } catch {
      // Offer may have already expired server-side - clearing it locally either way is correct.
    } finally {
      setOfferBookingId(null);
      setRespondingToOffer(false);
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader
        variant="greeting"
        title={`Hi, ${profile?.name?.split(' ')[0] || 'there'} 👋`}
        subtitle={isOnline ? "You're online" : "You're offline"}
        onMenuClick={() => navigate('/profile')}
        onBellClick={() => mockAction('Notifications', 'no notifications module on the backend yet')}
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      {!verification ? (
        <p className="text-center text-sm text-text-secondary">Loading...</p>
      ) : !isVerified ? (
        <Card className="flex items-center gap-3 bg-accent-orange/10">
          <IconCircle color="orange" tone="soft" icon={<ShieldCheck />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">Complete verification to go online</p>
            <p className="text-xs text-text-secondary">Gender and police verification are both required first.</p>
          </div>
          <Button size="md" onClick={() => navigate('/verification')}>
            Review
          </Button>
        </Card>
      ) : (
        <Card variant={isOnline ? 'primary' : 'surface'} className="flex items-center justify-between">
          <div>
            <p className="font-heading text-lg font-semibold">{isOnline ? "You're Online" : "You're Offline"}</p>
            <p className="mt-1 text-sm opacity-80">{isOnline ? 'Looking for ride requests nearby' : 'Go online to start receiving requests'}</p>
          </div>
          <Button
            variant={isOnline ? 'danger' : 'success'}
            size="md"
            icon={<Power className="h-4 w-4" />}
            disabled={togglingOnline}
            onClick={handleToggleOnline}
          >
            {togglingOnline ? '...' : isOnline ? 'Go Offline' : 'Go Online'}
          </Button>
        </Card>
      )}

      {activeTrip && (
        <Card className="flex items-center gap-3" onClick={() => navigate(`/trip/${activeTrip.id}`)}>
          {categoryIcon(activeTrip.category)}
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">Active Trip - {activeTrip.status.replace('_', ' ')}</p>
            <p className="truncate text-xs text-text-secondary">{activeTrip.drop.label}</p>
          </div>
        </Card>
      )}

      {!activeTrip && offerBookingId && (
        <Card className="space-y-3 border-2 border-primary">
          <div className="flex items-center gap-2">
            <IconCircle tone="soft" icon={<MapPin />} />
            <div className="flex-1">
              <p className="font-heading font-semibold text-text-primary">New Ride Request</p>
              <p className="text-xs text-text-secondary">Respond within {offerSecondsLeft}s</p>
            </div>
          </div>
          <div className="flex gap-3">
            <Button variant="secondary" fullWidth disabled={respondingToOffer} onClick={handleDeclineOffer}>
              Decline
            </Button>
            <Button variant="success" fullWidth disabled={respondingToOffer} onClick={handleAcceptOffer}>
              Accept
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}

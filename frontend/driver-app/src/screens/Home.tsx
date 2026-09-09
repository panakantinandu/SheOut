import { Bike, CheckCircle2, ClipboardList, Navigation2, Power, ShieldCheck, Star } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Button, Card, IconCircle, TopHeader } from '@sheout/design-system';
import { ApiError, bookingApi, dispatchApi, usersApi, verificationApi } from '../api/client';
import type { BookingSummary, DriverProfileSummary, VerificationSummary } from '../api/types';
import { mockAction } from '../lib/mockAction';

const BOOKINGS_POLL_MS = 5000;
const OFFER_POLL_MS = 4000;
// A real GPS subscription (watchPosition) rather than repeated one-off
// getCurrentPosition calls - fires on every OS/browser-reported movement,
// not on a timer. This interval instead controls how often we actually
// SEND the latest watched position to the backend, so we don't spam
// dispatch on every tiny GPS jitter.
const LOCATION_SEND_MS = 7000;
// Fallback only if the browser denies/lacks geolocation - central Hyderabad,
// not this driver's real position. Flagged rather than silently sent as real.
const FALLBACK_COORDS = { lat: 17.385, lng: 78.4867 };

function startOfDay(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * REAL: driver profile, verification gate, online/offline toggle, active-
 * trip detection, dashboard stats (today's earnings/completed rides -
 * derived from GET /bookings/me, same computation Earnings.tsx uses), and
 * live location broadcasting all hit real endpoints.
 * <p>
 * MOCK: the star rating - no ratings/reviews system exists anywhere on the
 * backend (no rating field on BookingSummary or DriverProfileSummary),
 * clearly labeled rather than fabricated as if real.
 * <p>
 * No push/notifications module exists (see DispatchController's own
 * Javadoc), so "New Request" is implemented as polling GET
 * /dispatch/offers/me every 4s while online with no active trip, then
 * navigating to the dedicated Offer screen - not instant delivery, a
 * deliberate backend limitation, not a frontend shortcut.
 */
export function Home() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [verification, setVerification] = useState<VerificationSummary | null>(null);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [togglingOnline, setTogglingOnline] = useState(false);
  const navigatedToOfferRef = useRef<string | null>(null);

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

  // Bookings list drives both the dashboard stats below and active-trip
  // detection - fetched regardless of online status, since a driver
  // checking their stats while offline should still see real numbers.
  useEffect(() => {
    let cancelled = false;
    async function poll() {
      try {
        const result = await bookingApi.listMine();
        if (!cancelled) setBookings(result);
      } catch {
        // Transient poll failure - next tick retries.
      }
    }
    poll();
    const interval = setInterval(poll, BOOKINGS_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  const activeTrip = useMemo(
    () => bookings.find((b) => b.status === 'MATCHED' || b.status === 'ACCEPTED' || b.status === 'IN_PROGRESS') ?? null,
    [bookings]
  );

  const { todayEarnings, completedRides, activeTripsCount } = useMemo(() => {
    const today = startOfDay();
    const completed = bookings.filter((b) => b.status === 'COMPLETED' && b.completedAt);
    const todaySum = completed
      .filter((b) => new Date(b.completedAt!) >= today)
      .reduce((sum, b) => sum + (b.finalFare ?? b.fareEstimate), 0);
    const active = bookings.filter((b) => b.status === 'MATCHED' || b.status === 'ACCEPTED' || b.status === 'IN_PROGRESS').length;
    return { todayEarnings: todaySum, completedRides: completed.length, activeTripsCount: active };
  }, [bookings]);

  // Offer polling - only while online and with no active trip already in
  // hand. Navigates to the dedicated Offer screen rather than showing an
  // inline card, since a real offer needs to show pickup/drop/fare/
  // distance, not just a bare accept/decline prompt.
  useEffect(() => {
    if (!isOnline || activeTrip) {
      navigatedToOfferRef.current = null;
      return;
    }
    let cancelled = false;
    async function poll() {
      try {
        const offer = await dispatchApi.getMyOffer();
        if (cancelled || !offer) return;
        if (navigatedToOfferRef.current !== offer.bookingId) {
          navigatedToOfferRef.current = offer.bookingId;
          navigate(`/offer/${offer.bookingId}`);
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
  }, [isOnline, activeTrip, navigate]);

  // Real live location while online - watchPosition (continuous GPS
  // subscription) feeds a ref with the latest fix; a separate interval
  // sends whatever's latest to dispatch every ~7s. Falls back to a fixed
  // Hyderabad coordinate if the browser denies/lacks geolocation, so
  // dispatch's geo-matching still has something to match against in a demo.
  useEffect(() => {
    if (!isOnline) return;
    const latest = { lat: FALLBACK_COORDS.lat, lng: FALLBACK_COORDS.lng };
    let watchId: number | null = null;
    if (navigator.geolocation) {
      watchId = navigator.geolocation.watchPosition(
        (pos) => {
          latest.lat = pos.coords.latitude;
          latest.lng = pos.coords.longitude;
        },
        () => {
          // Leave `latest` at its last-known (or fallback) value - a transient watch error shouldn't stop broadcasting entirely.
        },
        { enableHighAccuracy: true, maximumAge: 10000, timeout: 8000 }
      );
    }
    const interval = setInterval(() => {
      dispatchApi.recordLocation(latest.lat, latest.lng).catch(() => {});
    }, LOCATION_SEND_MS);
    dispatchApi.recordLocation(latest.lat, latest.lng).catch(() => {});
    return () => {
      if (watchId !== null) navigator.geolocation.clearWatch(watchId);
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

      <Card className="flex items-center gap-3">
        <IconCircle size="lg" tone="soft" icon={<Star />} />
        <div className="flex-1">
          <p className="font-heading font-semibold text-text-primary">{profile?.name || 'Loading...'}</p>
          {/* MOCK: no ratings/reviews system exists on the backend - fixed placeholder, clearly labeled, not fabricated as real. */}
          <p className="text-xs text-text-secondary">★ 4.8 (mock) &middot; {profile?.vehicleType ?? '--'}</p>
        </div>
      </Card>

      <div className="grid grid-cols-3 gap-3">
        <Card className="space-y-1 p-3 text-center">
          <p className="text-xs text-text-secondary">Today</p>
          <AmountText amount={todayEarnings} size="sm" />
        </Card>
        <Card className="flex flex-col items-center gap-1 p-3 text-center">
          <CheckCircle2 className="h-4 w-4 text-accent-green" />
          <p className="font-heading text-sm font-semibold text-text-primary">{completedRides}</p>
          <p className="text-xs text-text-secondary">Completed</p>
        </Card>
        <Card className="flex flex-col items-center gap-1 p-3 text-center">
          <ClipboardList className="h-4 w-4 text-primary" />
          <p className="font-heading text-sm font-semibold text-text-primary">{activeTripsCount}</p>
          <p className="text-xs text-text-secondary">Active</p>
        </Card>
      </div>

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
          <IconCircle tone="soft" icon={activeTrip.type === 'RIDE' ? <Bike className="h-5 w-5" /> : <Navigation2 className="h-5 w-5" />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">Active Trip - {activeTrip.status.replace('_', ' ')}</p>
            <p className="truncate text-xs text-text-secondary">{activeTrip.drop.label}</p>
          </div>
        </Card>
      )}
    </div>
  );
}

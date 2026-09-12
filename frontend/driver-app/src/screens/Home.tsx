import { Bell, Bike, CheckCircle2, ClipboardList, CloudOff, IndianRupee, MapPinOff, Navigation2, Power, RefreshCw, ShieldCheck, User } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Button, Card, IconCircle, LiveMap, TopHeader, bookingStatusLabel, vehicleLabel } from '@sheout/design-system';
import { ApiError, bookingApi, dispatchApi, usersApi, verificationApi } from '../api/client';
import type { BookingSummary, DriverProfileSummary, VerificationSummary } from '../api/types';
import { useLocationBroadcast } from '../lib/useLocationBroadcast';

const BOOKINGS_POLL_MS = 5000;
const OFFER_POLL_MS = 4000;

function startOfDay(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * What a section shows when its request failed: what did not load, what the
 * server said, and a way to ask again. A screen that can only ever say
 * "Loading..." is a dead end - the partner cannot tell a slow network from a
 * broken one, and has nothing to do about either.
 */
function LoadError({ title, detail, onRetry }: { title: string; detail: string; onRetry: () => void }) {
  return (
    <Card tone="danger" className="flex items-start gap-3">
      <IconCircle color="red" tone="soft" icon={<CloudOff />} />
      <div className="min-w-0 flex-1">
        <p className="font-heading font-semibold text-text-primary">{title}</p>
        <p className="mt-0.5 text-xs text-text-secondary">{detail}</p>
      </div>
      <Button variant="secondary" size="md" icon={<RefreshCw className="h-4 w-4" />} onClick={onRetry}>
        Try again
      </Button>
    </Card>
  );
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
  const [profileError, setProfileError] = useState<string | null>(null);
  const [verificationError, setVerificationError] = useState<string | null>(null);
  const [togglingOnline, setTogglingOnline] = useState(false);
  const navigatedToOfferRef = useRef<string | null>(null);

  /**
   * Load failures are held separately from `error`, which belongs to the
   * online toggle. They used to share one banner while the card below went
   * on saying "Loading...", so a failed fetch left a partner looking at a
   * word that would never change, with nothing to press.
   */
  const loadProfile = useCallback(() => {
    setProfileError(null);
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setProfileError(err instanceof ApiError ? err.message : 'Could not load your profile'));
  }, []);

  /**
   * This swallowed its failure entirely and set the summary to null, which
   * is indistinguishable from "still loading" - so the same dead end, on the
   * one section that decides whether a partner can go online at all.
   */
  const loadVerification = useCallback(() => {
    setVerificationError(null);
    verificationApi
      .getMyStatus()
      .then(setVerification)
      .catch((err) =>
        setVerificationError(err instanceof ApiError ? err.message : 'Could not check your verification status')
      );
  }, []);

  useEffect(() => {
    loadProfile();
    loadVerification();
  }, [loadProfile, loadVerification]);

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

  const { todayEarnings, completedRides, activeTripsCount, recentTrips } = useMemo(() => {
    const today = startOfDay();
    const completed = bookings.filter((b) => b.status === 'COMPLETED' && b.completedAt);
    const completedToday = completed.filter((b) => new Date(b.completedAt!) >= today);
    const todaySum = completedToday.reduce((sum, b) => sum + (b.finalFare ?? b.fareEstimate), 0);
    const active = bookings.filter((b) => b.status === 'MATCHED' || b.status === 'ACCEPTED' || b.status === 'IN_PROGRESS').length;
    return {
      todayEarnings: todaySum,
      // Today's count, not all-time. Sitting an all-time total beside
      // "Today's Earnings" in one card read as though both covered the same
      // period, so a driver with 2 lifetime trips and nothing today saw
      // "₹0" next to "2" and had no way to tell which was which.
      completedRides: completedToday.length,
      activeTripsCount: active,
      recentTrips: completed
        .slice()
        .sort((a, b2) => new Date(b2.completedAt!).getTime() - new Date(a.completedAt!).getTime())
        .slice(0, 3),
    };
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

  // Broadcast position while online. Shared with Trip via the hook so it
  // survives the navigation into a trip - see useLocationBroadcast.
  const location = useLocationBroadcast(isOnline);

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
      {/* Centred "Partner Dashboard" title, per the mockup. The bell moves
          into the right slot rather than disappearing with the greeting
          header - it is this app's only route to the notifications screen.
          <p>
          No back arrow: this is the app's top-level destination, reached
          from the tab bar, and there is nothing behind it. It used to carry
          one wired to navigate(-1), which either did nothing on a fresh
          launch or threw the driver back to whatever screen they had just
          deliberately left - a back arrow that implies a hierarchy this
          screen does not sit in. */}
      <TopHeader
        variant="plain"
        centerTitle
        title="Partner Dashboard"
        rightSlot={
          <button
            type="button"
            aria-label="Notifications"
            onClick={() => navigate('/notifications')}
            className="flex h-9 w-9 items-center justify-center rounded-full text-text-primary hover:bg-background"
          >
            <Bell className="h-5 w-5" />
          </button>
        }
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      {/* A driver who is online but not actually sharing a position is in no
          dispatch search results at all. That used to be invisible: the app
          broadcast a fixed city-centre coordinate instead, so everything
          looked normal while requests went to drivers who were really
          there. Say it plainly instead. */}
      {isOnline && location.status === 'blocked' && (
        <Card tone="danger" className="flex items-start gap-3">
          <IconCircle color="red" tone="soft" icon={<MapPinOff />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">Location not shared</p>
            <p className="text-xs text-text-secondary">{location.error}</p>
          </div>
        </Card>
      )}

      {profileError ? (
        <LoadError title="Could not load your profile" detail={profileError} onRetry={loadProfile} />
      ) : (
      <Card className="flex items-center gap-3">
        <IconCircle size="lg" tone="soft" icon={<User />} />
        <div className="flex-1">
          <p className="font-heading font-semibold text-text-primary">{profile?.name || 'Loading your profile...'}</p>
          <div className="flex items-center gap-2">
            {/* Online/offline dot, as the mockup shows beside the name. Real
                state from the profile, not decoration. */}
            <span className="flex items-center gap-1.5 text-xs text-text-secondary">
              <span
                className={`h-2 w-2 rounded-full ${isOnline ? 'bg-accent-green' : 'bg-text-secondary/40'}`}
                aria-hidden="true"
              />
              {isOnline ? 'Online' : 'Offline'}
            </span>
            {/* MOCK: no ratings/reviews system exists on the backend - fixed placeholder, clearly labeled, not fabricated as real. */}
            <span className="text-xs text-text-secondary">&middot; ★ 4.8 (mock) &middot; {vehicleLabel(profile?.vehicleType)}</span>
          </div>
        </div>
      </Card>
      )}

      {/* One card split by dividers, matching the mockup, rather than three
          separate cards with gaps between them. */}
      <Card className="flex items-stretch p-0">
        {/* Icon, then figure, then label - the same order as the two
            columns beside it. This one used to run label-then-figure with
            no icon, so its number sat a line lower than its neighbours and
            the row read as misaligned. */}
        <div className="flex flex-1 flex-col items-center gap-1 p-3 text-center">
          <IndianRupee className="h-4 w-4 text-primary" />
          <AmountText amount={todayEarnings} size="sm" />
          <p className="text-xs text-text-secondary">Earned Today</p>
        </div>
        <div className="w-px self-stretch bg-border" aria-hidden="true" />
        <div className="flex flex-1 flex-col items-center gap-1 p-3 text-center">
          <CheckCircle2 className="h-4 w-4 text-accent-green" />
          <p className="font-heading text-sm font-semibold text-text-primary">{completedRides}</p>
          <p className="text-xs text-text-secondary">Rides Today</p>
        </div>
        <div className="w-px self-stretch bg-border" aria-hidden="true" />
        <div className="flex flex-1 flex-col items-center gap-1 p-3 text-center">
          <ClipboardList className="h-4 w-4 text-primary" />
          <p className="font-heading text-sm font-semibold text-text-primary">{activeTripsCount}</p>
          <p className="text-xs text-text-secondary">Active Trips</p>
        </div>
      </Card>

      {verificationError ? (
        <LoadError title="Could not check your verification" detail={verificationError} onRetry={loadVerification} />
      ) : !verification ? (
        <p className="text-center text-sm text-text-secondary">Checking your verification...</p>
      ) : !isVerified ? (
        <Card tone="warning" className="flex items-center gap-3">
          <IconCircle color="orange" tone="soft" icon={<ShieldCheck />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">Complete verification to go online</p>
            <p className="text-xs text-text-secondary">Gender and police verification are both required first.</p>
          </div>
          <Button size="md" onClick={() => navigate('/verification')}>
            Review
          </Button>
        </Card>
      ) : !profile ? (
        // Verified, but the profile never arrived, so the current online
        // status is unknown. handleToggleOnline returns early without one,
        // which would have made this a button that does nothing at all. The
        // profile card above is already offering the retry.
        null
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
            <p className="font-heading font-semibold text-text-primary">Active Trip - {bookingStatusLabel(activeTrip.status)}</p>
            <p className="truncate text-xs text-text-secondary">{activeTrip.drop.label}</p>
          </div>
        </Card>
      )}

      {/* Where the driver actually is, while online. A dashboard that is
          mostly empty space tells a partner nothing; every real driver app
          puts them on a map. Real position from the same broadcast the
          customer's tracking map consumes - no marker at all until the
          device gives a genuine fix. */}
      {isOnline && location.position && (
        <div className="space-y-1">
          <LiveMap
            markers={[{ key: 'me', lat: location.position.lat, lng: location.position.lng, label: 'You', kind: 'driver' }]}
            className="h-52"
          />
          <p className="text-xs text-text-secondary">Your position updates as your device reports movement.</p>
        </div>
      )}

      {recentTrips.length > 0 && (
        <div>
          <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Recent trips</h2>
          <Card className="divide-y divide-border p-0">
            {recentTrips.map((trip) => (
              <div key={trip.id} className="flex items-center gap-3 p-4">
                <IconCircle tone="soft" size="sm" icon={<Bike />} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-text-primary">{trip.drop.label}</p>
                  <p className="text-xs text-text-secondary">{new Date(trip.completedAt!).toLocaleString()}</p>
                </div>
                <AmountText amount={trip.finalFare ?? trip.fareEstimate} />
              </div>
            ))}
          </Card>
        </div>
      )}
    </div>
  );
}

import { Bell, Bike, CheckCircle2, ClipboardList, CloudOff, Globe2, IndianRupee, MapPinOff, Navigation2, Power, RefreshCw, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AggregateRatingText,
  AmountText,
  Avatar,
  BellBadge,
  Button,
  Card,
  IconCircle,
  LiveMap,
  PushPromptCard,
  SkeletonCard,
  StatusDot,
  TopHeader,
  bookingStatusLabel,
  brandIllustration,
  useCountUp,
  usePushMessages,
  usePushNotifications,
  useUnreadNotifications,
  vehicleLabel,
} from '@sheout/design-system';
import {
  ApiError,
  PUSH_TOKEN_KEY,
  bookingApi,
  dispatchApi,
  notificationsApi,
  pushApi,
  ratingsApi,
  usersApi,
  verificationApi,
} from '../api/client';
import type { AggregateRating, BookingSummary, DriverProfileSummary, VerificationSummary } from '../api/types';
import { RatingPrompt } from '../components/RatingPrompt';
import { readPositionOnce, useShareLocation } from '../lib/LocationBroadcastContext';
import { playOfferChime, unlockChime } from '../lib/offerChime';

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
 * The star rating is REAL now, and no longer labelled as mock: it is this
 * partner's own average from GET /ratings/me, built from what riders
 * actually submitted after completed trips. It used to be a hardcoded 4.8
 * with "(mock)" beside it, because no ratings system existed.
 * <p>
 * An account nobody has rated yet reads "Not rated yet" rather than a
 * number. Showing 0.0 to a partner on her first night would tell her the
 * platform thinks she is the worst driver on it.
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
  // Set when the server says she is not anywhere SheOut operates. Not an
  // error - a standing fact about where she is, which no retry changes.
  const [outOfArea, setOutOfArea] = useState<string | null>(null);
  const [rating, setRating] = useState<AggregateRating | null>(null);
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

  // Reloaded after this partner rates somebody too, because rating a rider
  // is the moment she is most likely to look at her own score.
  const loadRating = useCallback(() => {
    ratingsApi
      .mine()
      .then(setRating)
      .catch(() => {
        // Leaves the line reading "Not rated yet" rather than breaking the
        // card. Nothing on this screen depends on it.
      });
  }, []);

  useEffect(() => {
    loadProfile();
    loadVerification();
    loadRating();
  }, [loadProfile, loadVerification, loadRating]);

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

  // The two counts beside the money, counting up with it so the row settles
  // as one. Rounded, because a ride and a half is not a thing.
  const ridesShown = Math.round(useCountUp(completedRides, true));
  const activeTripsShown = Math.round(useCountUp(activeTripsCount, true));

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
          playOfferChime();
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

  // Share position while online, and while a trip is live even if she has
  // somehow gone offline with one in hand - a rider watching a partner
  // approach should not lose her because of a toggle. The subscription
  // itself lives above the router, so it is not dropped when this screen
  // unmounts into an offer or a trip; see LocationBroadcastContext.
  const location = useShareLocation(isOnline || Boolean(activeTrip));

  // Straight after sign-in the dashboard is the first screen, so this is
  // where she is asked to turn alerts on - offers only last 15 seconds.
  const push = usePushNotifications(pushApi, PUSH_TOKEN_KEY, true);
  const unreadCount = useUnreadNotifications(notificationsApi.unreadCount, true);

  // An offer push arriving while the dashboard is open: go to it now rather
  // than waiting for the next poll.
  usePushMessages(
    useCallback(
      (message: { urgency: string; link: string | null }) => {
        if (message.urgency !== 'ALERT' || !message.link?.startsWith('/offer/')) return;
        const bookingId = message.link.slice('/offer/'.length);
        if (navigatedToOfferRef.current === bookingId) return;
        navigatedToOfferRef.current = bookingId;
        playOfferChime();
        navigate(message.link);
      },
      [navigate]
    )
  );

  async function handleToggleOnline() {
    if (!profile) return;
    // This tap is what lets the offer chime play later - see offerChime.ts.
    unlockChime();
    setTogglingOnline(true);
    setError(null);
    setOutOfArea(null);
    try {
      if (isOnline) {
        // Stopping work asks nothing and checks nothing. She must be able to
        // go offline anywhere, including outside the service area and with
        // location switched off.
        setProfile(await usersApi.setOnlineStatus('OFFLINE'));
        return;
      }
      // One fix, taken now because she asked to start working. The server
      // decides whether it is anywhere SheOut operates; this app does not
      // second-guess it with its own copy of the boundary.
      const here = await readPositionOnce(location.position);
      if (!here) {
        setError('We need your location to send you nearby trips. Allow location access and try again.');
        return;
      }
      setProfile(await usersApi.setOnlineStatus('ONLINE', here));
    } catch (err) {
      // A missing photo is the one refusal she can fix herself, in under a
      // minute, so it goes straight to the screen that fixes it rather than
      // leaving her reading an error on a page with no way forward. The
      // backend gives it its own machine code precisely so this is possible.
      if (
        err instanceof ApiError &&
        (err.body?.error === 'PROFILE_PHOTO_REQUIRED' || err.body?.error === 'DATE_OF_BIRTH_REQUIRED')
      ) {
        navigate('/profile');
        setError(err.message);
        return;
      }
      // Being in the wrong part of the world is not an error she can retry
      // away, so it gets a standing explanation rather than a red line that
      // reads like something went wrong.
      if (err instanceof ApiError && err.body?.error === 'OUTSIDE_SERVICE_AREA') {
        setOutOfArea(err.message);
        return;
      }
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
            aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
            onClick={() => navigate('/notifications')}
            className="relative flex h-9 w-9 items-center justify-center rounded-full text-text-primary hover:bg-background"
          >
            <Bell className="h-5 w-5" />
            {unreadCount ? <BellBadge count={unreadCount} /> : null}
          </button>
        }
      />

      {/* The rider app opens on an illustrated banner and this screen opened
          on white space above an alert card. Same treatment, her side of it:
          she is not being sold a safe ride, she is the person providing one,
          so it greets her by name and says what the day looks like. Same
          brand illustration the rider app uses - one asset, downloaded once,
          rather than a second drawing for one banner. */}
      <Card variant="primary" className="relative overflow-hidden">
        <div className="relative z-10 max-w-[64%]">
          <p className="font-heading text-lg font-semibold">
            {profile?.name ? `Welcome back, ${profile.name.split(' ')[0]}` : 'Welcome back'}
          </p>
          <p className="mt-1 text-sm opacity-90">
            {isOnline ? 'You are online and taking requests' : 'Go online when you are ready to drive'}
          </p>
        </div>
        <img
          src={brandIllustration}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-3 -right-3 h-28 w-28 object-contain opacity-95"
        />
      </Card>

      {push.shouldPrompt && (
        <PushPromptCard audience="partner" busy={push.busy} onTurnOn={push.turnOn} onDismiss={push.dismiss} />
      )}

      {error && <p className="text-sm text-danger">{error}</p>}

      {/* Not a red error, because nothing failed and nothing can be retried.
          SheOut runs in one city; a partner opening this app from anywhere
          else needs to be told that plainly rather than left tapping a
          button that used to say "Looking for ride requests nearby" while
          dispatch had no possible trip to send her. */}
      {outOfArea && (
        <Card tone="warning" className="flex items-start gap-3">
          <IconCircle color="orange" tone="soft" icon={<Globe2 />} />
          <div className="min-w-0 flex-1">
            <p className="font-heading font-semibold text-text-primary">You are outside our service area</p>
            <p className="mt-0.5 text-xs text-text-secondary">{outOfArea}</p>
          </div>
        </Card>
      )}

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
      ) : !profile ? (
        <SkeletonCard lines={2} label="Loading your profile" />
      ) : (
      <Card className="flex items-center gap-3">
        {/* Her own photo, the one riders see. It used to be a generic person
            glyph even for partners who had uploaded one. */}
        <Avatar url={profile.profilePhotoUrl} name={profile.name} size="lg" />
        <div className="flex-1">
          <p className="font-heading font-semibold text-text-primary">{profile.name || 'Add your name'}</p>
          <div className="flex items-center gap-2">
            {/* Online/offline dot, as the mockup shows beside the name. Real
                state from the profile, not decoration - and it breathes while
                she is online, which is the one place this screen says "the
                app is awake and listening" without words. */}
            <span className="flex items-center gap-1.5 text-xs text-text-secondary">
              <StatusDot live={isOnline} />
              {isOnline ? 'Online' : 'Offline'}
            </span>
            {/* Real, from this partner's own ratings. See the file header. */}
            <span className="text-xs text-text-secondary">
              &middot;{' '}
              <AggregateRatingText
                averageStars={rating?.averageStars}
                totalRatings={rating?.totalRatings}
                emptyLabel="Not rated yet"
              />{' '}
              &middot; {vehicleLabel(profile.vehicleType)}
            </span>
          </div>
        </div>
      </Card>
      )}

      {/* One card split by dividers, matching the mockup, rather than three
          separate cards with gaps between them.
          <p>
          Each figure now sits under a coloured IconCircle, the same pattern
          the rider app uses for Quick Access - bare monochrome glyphs made
          three different facts read as one undifferentiated row of numbers.
          The money counts up when it first lands; the counts beside it do
          too, so the row settles together rather than one figure moving. */}
      <Card className="flex items-stretch p-0">
        <div className="flex flex-1 flex-col items-center gap-1.5 p-3 text-center">
          <IconCircle size="sm" tone="soft" color="primary" icon={<IndianRupee />} />
          <AmountText amount={todayEarnings} size="sm" animate />
          <p className="text-xs text-text-secondary">Earned Today</p>
        </div>
        <div className="w-px self-stretch bg-border" aria-hidden="true" />
        <div className="flex flex-1 flex-col items-center gap-1.5 p-3 text-center">
          <IconCircle size="sm" tone="soft" color="green" icon={<CheckCircle2 />} />
          <p className="font-heading text-sm font-semibold tabular-nums text-text-primary">{ridesShown}</p>
          <p className="text-xs text-text-secondary">Rides Today</p>
        </div>
        <div className="w-px self-stretch bg-border" aria-hidden="true" />
        <div className="flex flex-1 flex-col items-center gap-1.5 p-3 text-center">
          <IconCircle size="sm" tone="soft" color="orange" icon={<ClipboardList />} />
          <p className="font-heading text-sm font-semibold tabular-nums text-text-primary">{activeTripsShown}</p>
          <p className="text-xs text-text-secondary">Active Trips</p>
        </div>
      </Card>

      {verificationError ? (
        <LoadError title="Could not check your verification" detail={verificationError} onRetry={loadVerification} />
      ) : !verification ? (
        <SkeletonCard lines={2} label="Checking your verification" />
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

      {/* The dashboard is where a partner lands after completing a trip, so
          this is where she is asked. It asks about whatever is actually
          waiting, decided by the server, not by this screen's idea of what
          just finished. */}
      <RatingPrompt counterpartLabel="your rider" onRated={loadRating} />
    </div>
  );
}

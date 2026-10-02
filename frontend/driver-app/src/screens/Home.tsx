import { Bell, CloudOff, Hourglass, Menu, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ASSISTANT_NAME, AggregateRatingText, AssistantFab, BellBadge, Button, Card, IconCircle, PushPromptCard, RotatingText, SkeletonList, SkyIcon, bookingStatusLabel, useDayPart, usePushMessages, usePushNotifications, useUnreadNotifications, ServiceArt, serviceArtFor } from '@sheout/design-system';
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
import { apiErrorText } from '../lib/apiErrors';
import { useAppDrawer } from '../components/AppDrawer';
import type { AggregateRating, BookingSummary, DriverProfileSummary, PaymentHold, VerificationSummary } from '../api/types';
import { RatingPrompt } from '../components/RatingPrompt';
import { readPositionOnce, useShareLocation } from '../lib/LocationBroadcastContext';
import { PartnerSos } from '../components/PartnerSos';
import { playOfferChime, unlockChime } from '../lib/offerChime';
import { useTranslation } from '@sheout/design-system';
import { deriveHeroState, useNetworkUp, useNow } from '../lib/driverStatus';
import { StatusHero } from '../components/home/StatusHero';
import { LocationHelpSheet } from '../components/home/LocationHelpSheet';
import { EarningsCard } from '../components/home/EarningsCard';
import { RecentTrips } from '../components/home/RecentTrips';
import { BrandStrip } from '../components/home/BrandStrip';
import { ServiceClosedStrip } from '../components/home/ServiceClosedStrip';
import { DRIVER_HOME_MAP_ENABLED, HomeMapCard } from '../components/home/HomeMapCard';

const BOOKINGS_POLL_MS = 5000;
const OFFER_POLL_MS = 4000;
/** How many finished trips the Home list shows; the rest are a tap away in Bookings. */
const RECENT_TRIPS = 5;

function finishedAt(b: BookingSummary): number {
  return new Date(b.completedAt ?? b.cancelledAt ?? b.requestedAt).getTime();
}

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
  const { t } = useTranslation();
  return (
    <Card tone="danger" className="flex items-start gap-3">
      <IconCircle color="red" tone="soft" icon={<CloudOff />} />
      <div className="min-w-0 flex-1">
        <p className="font-heading text-card-title text-text-primary">{title}</p>
        <p className="mt-1 text-xs text-text-secondary">{detail}</p>
      </div>
      <Button variant="secondary" size="md" icon={<RefreshCw className="h-4 w-4" />} onClick={onRetry}>
        {t('common.tryAgain')}
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
  const { t } = useTranslation();
  const drawer = useAppDrawer();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [verification, setVerification] = useState<VerificationSummary | null>(null);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  /** The first bookings poll has answered - until then the trip list is a skeleton, not "no trips". */
  const [bookingsLoaded, setBookingsLoaded] = useState(false);
  const [locationHelpOpen, setLocationHelpOpen] = useState(false);
  /** A just-ended trip still waiting for the rider's payment - no new offers until it clears. */
  const [paymentHold, setPaymentHold] = useState<PaymentHold | null>(null);
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
      .catch((err) => setProfileError(err instanceof ApiError ? err.message : t('profile.loadError')));
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
        setVerificationError(err instanceof ApiError ? err.message : t('home.verificationError'))
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
        // The hold is extra information; a failure fetching it must never
        // stop the trip list itself from refreshing.
        const [result, hold] = await Promise.all([
          // The newest 50: her live trip and today's trips are always among them - see the endpoint.
          bookingApi.listRecent(50),
          bookingApi.getPaymentHold().catch(() => null),
        ]);
        if (!cancelled) {
          setBookings(result);
          setPaymentHold(hold);
          setBookingsLoaded(true);
        }
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
    // Paid trips only. A fare still waiting for the rider is not earned yet,
    // and counting it would show her money that may never arrive.
    const completed = bookings.filter((b) => b.status === 'COMPLETED' && b.completedAt && b.paymentSettledAt);
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
      // Finished trips, newest first: completed ones (paid or still owed)
      // and cancelled ones, as Bookings lists them.
      recentTrips: bookings
        .filter((b) => b.status === 'COMPLETED' || b.status === 'CANCELLED')
        .sort((a, b2) => finishedAt(b2) - finishedAt(a))
        .slice(0, RECENT_TRIPS),
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

  // Where she really stands, as one state - see deriveHeroState. The clock
  // only ticks while she is online, which is when a report can go stale.
  const networkUp = useNetworkUp();
  const now = useNow(isOnline);
  const heroState = deriveHeroState({
    profileLoaded: Boolean(profile),
    verification: !verification ? 'loading' : isVerified ? 'verified' : 'pending',
    isOnline,
    permission: location.permission,
    locationStatus: location.status,
    lastReportAt: location.lastReportAt,
    activeSince: location.activeSince,
    networkUp,
    now,
  });

  /**
   * "Turn on location": ask the browser, which shows its prompt where it
   * still can; if it will not (access already blocked), the steps sheet.
   * Either way the card recovers by itself once access is granted.
   */
  function askForLocation() {
    if (!navigator.geolocation) {
      setLocationHelpOpen(true);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      () => {
        setLocationHelpOpen(false);
        location.restart();
      },
      () => setLocationHelpOpen(true),
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }

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
        setError(t('home.needLocation'));
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
      // The start-of-shift selfie (and helmet photo). Its own screen, which
      // goes online by itself once she passes.
      if (
        err instanceof ApiError &&
        (err.body?.error === 'SHIFT_CHECK_REQUIRED' || err.body?.error === 'SHIFT_CHECK_UNDER_REVIEW')
      ) {
        navigate('/shift-check', { state: { goOnline: true } });
        return;
      }
      // Being in the wrong part of the world is not an error she can retry
      // away, so it gets a standing explanation rather than a red line that
      // reads like something went wrong.
      if (err instanceof ApiError && err.body?.error === 'OUTSIDE_SERVICE_AREA') {
        setOutOfArea(t('apiError.OUTSIDE_SERVICE_AREA'));
        return;
      }
      setError(apiErrorText(err, 'home.statusError'));
    } finally {
      setTogglingOnline(false);
    }
  }

  const firstName = profile?.name?.trim().split(/\s+/)[0];
  // Good morning / afternoon / evening, the sky beside it, and a line for
  // that time of day changing softly under it.
  const dayPart = useDayPart();
  const dayLines = [t(`home.day.${dayPart}.line1`), t(`home.day.${dayPart}.line2`)];

  return (
    <div className="space-y-4">
      {/* An app bar: the menu, SheOut Partner by name, then the bell and SOS.
          SOS keeps its word label and its sheet; it no longer takes a row of
          its own. Always here, online or not: the drive home after the last
          trip is still a drive alone. */}
      <header className="flex items-center gap-2" data-testid="home-header">
        <button
          type="button"
          onClick={drawer.open}
          aria-label={t('home.menu')}
          className="-ml-2 flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-text-primary hover:bg-background"
        >
          <Menu className="h-6 w-6" aria-hidden="true" />
        </button>
        <div className="min-w-0 flex-1">
          <BrandStrip />
        </div>
        <button
          type="button"
          aria-label={unreadCount ? t('home.notificationsUnread', { count: unreadCount }) : t('notifications.title')}
          onClick={() => navigate('/notifications')}
          className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-text-primary hover:bg-background"
        >
          <Bell className="h-5 w-5" />
          {unreadCount ? <BellBadge count={unreadCount} /> : null}
        </button>
        <div className="shrink-0">
          <PartnerSos />
        </div>
      </header>

      {/* Good morning, with the sky, a line for this time of day, and how riders rate her. */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-2 break-words font-heading text-section leading-tight text-text-primary" data-testid="home-greeting">
            <SkyIcon part={dayPart} size={26} />
            <span className="min-w-0">{t(`home.day.${dayPart}.greeting`, { name: firstName || t('home.there') })}</span>
          </p>
          <RotatingText lines={dayLines} className="mt-0.5 text-sm text-text-secondary" />
        </div>
        {/* AggregateRatingText draws its own star; an icon here doubled it. */}
        <span className="mt-1 inline-flex shrink-0 items-center gap-1 rounded-full bg-primary-light px-2 py-0.5 text-xs font-semibold text-primary" data-testid="rating-chip">
          <AggregateRatingText averageStars={rating?.averageStars} totalRatings={rating?.totalRatings} emptyLabel={t('home.notRated')} />
        </span>
      </div>

      {/* Outside the operating hours, or paused: no offers will come, and she should know why. */}
      <ServiceClosedStrip />

      {/* Where she stands, and the one thing to do about it. */}
      {profileError ? (
        <LoadError title={t('profile.loadError')} detail={profileError} onRetry={loadProfile} />
      ) : verificationError ? (
        <LoadError title={t('home.verificationError')} detail={verificationError} onRetry={loadVerification} />
      ) : (
        <StatusHero
          state={heroState}
          toggling={togglingOnline}
          onGoOnline={handleToggleOnline}
          onGoOffline={handleToggleOnline}
          onTurnOnLocation={() => (location.permission === 'denied' ? setLocationHelpOpen(true) : askForLocation())}
          onReviewVerification={() => navigate('/verification')}
          message={outOfArea ?? error}
        />
      )}

      {/* A trip in hand comes before everything below it. */}
      {activeTrip && (
        <Card className="flex items-center gap-3 p-3" onClick={() => navigate(`/trip/${activeTrip.id}`)} data-testid="home-active-trip">
          <ServiceArt kind={serviceArtFor(activeTrip.category)} size="md" />
          <div className="min-w-0 flex-1">
            <p className="font-heading text-card-title text-text-primary">{t('home.activeTrip', { status: bookingStatusLabel(activeTrip.status) })}</p>
            <p className="truncate text-xs text-text-secondary">{activeTrip.drop.label}</p>
          </div>
        </Card>
      )}

      {/* Why no requests are arriving, if a fare is outstanding. The server
          keeps offers away until the rider pays or the hold runs out; this
          says so rather than leaving her online and wondering. */}
      {paymentHold && !activeTrip && (
        <Card
          tone="warning"
          className="flex items-center gap-3"
          onClick={() => navigate(`/trip/${paymentHold.bookingId}`)}
          data-testid="dashboard-payment-hold"
        >
          <IconCircle tone="soft" color="orange" icon={<Hourglass className="h-5 w-5" />} />
          <div className="flex-1">
            <p className="font-heading text-card-title text-text-primary">
              {t('home.holdTitle', { amount: paymentHold.amount.toFixed(0) })}
            </p>
            <p className="text-xs text-text-secondary">
              {paymentHold.holdUntil
                ? t('home.holdBodyUntil', { time: new Date(paymentHold.holdUntil).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) })
                : t('home.holdBody')}
            </p>
          </div>
        </Card>
      )}

      {/* Where she is, while online - behind a flag while its cost is measured. */}
      {DRIVER_HOME_MAP_ENABLED && isOnline && location.position && <HomeMapCard position={location.position} />}

      <EarningsCard amount={todayEarnings} rides={completedRides} activeTrips={activeTripsCount} onOpen={() => navigate('/earnings')} />

      {push.shouldPrompt && (
        <PushPromptCard audience="partner" busy={push.busy} onTurnOn={push.turnOn} onDismiss={push.dismiss} />
      )}

      {bookingsLoaded ? (
        <RecentTrips trips={recentTrips} onSeeAll={() => navigate('/bookings')} onOpen={(id) => navigate(`/trip/${id}`)} />
      ) : (
        <SkeletonList rows={3} label={t('home.loadingTrips')} />
      )}

      <LocationHelpSheet open={locationHelpOpen} onClose={() => setLocationHelpOpen(false)} onAskAgain={askForLocation} />

      {/* The dashboard is where a partner lands after completing a trip, so
          this is where she is asked. It asks about whatever is actually
          waiting, decided by the server, not by this screen's idea of what
          just finished. */}
      <RatingPrompt counterpartLabel={t('common.yourRider')} onRated={loadRating} />

      {/* Room at the foot, so the assistant in the corner never sits on the last trip. */}
      <div className="h-14" aria-hidden="true" />

      {/* The assistant, standing by in the corner, as in the rider app. */}
      <AssistantFab
        lines={[t('home.agent.hello', { name: firstName || t('home.there') }), t('home.agent.line1'), t('home.agent.line2')]}
        label={t('home.agent.open', { name: ASSISTANT_NAME })}
        onOpen={() => navigate('/help/assistant')}
      />
    </div>
  );
}

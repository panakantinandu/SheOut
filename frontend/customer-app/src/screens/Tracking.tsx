import { CheckCircle2, Headphones, MessageCircle, Navigation, Radio, SearchX, ShieldAlert, Star, Wallet as WalletIcon, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  AggregateRatingText,
  AmountText,
  Avatar,
  Button,
  CancelReasonDialog,
  Card,
  CUSTOMER_CANCELLATION_REASONS,
  IconCircle,
  LiveMap,
  OpenInMapsButton,
  PickupCodeCard,
  SafetyText,
  SkeletonCard,
  StatusBadge,
  SuccessCheck,
  TopHeader,
  bookingStatusLabel,
  vehicleLabel,
} from '@sheout/design-system';
import type { CancellationReason as SharedCancellationReason, MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi, chatApi, dispatchApi } from '../api/client';
import type { AssignedDriver, BookingStatus, BookingSummary, DriverLocation } from '../api/types';
import { RatingPrompt } from '../components/RatingPrompt';
import { TripPaymentCard } from '../components/TripPaymentCard';
import { apiErrorText } from '../lib/apiErrors';
import { mapsLink, shareViaDevice } from '../lib/emergency';
import { useTranslation } from '@sheout/design-system';

/** A conservative city speed for a two-wheeler, for the "about N min" line. */
const CITY_SPEED_KMH = 20;

/** Great-circle distance - an honest lower bound on the road distance. */
function straightLineKm(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(h));
}

const POLL_INTERVAL_MS = 3000;

/**
 * Used only until the real figure arrives from the backend, which serves it
 * so the two cannot drift. Matches the backend default; if it is ever
 * retuned there and this is forgotten, the fetched value still wins.
 */
const DEFAULT_SEARCH_TIMEOUT_SECONDS = 90;

/**
 * The statuses in which a rider may see who her partner is.
 * <p>
 * MATCHED is deliberately absent, and this list has to agree with the
 * server's - the server is the one that enforces it, and this only decides
 * whether to ask. A driverId exists from MATCHED onwards, so anything
 * keyed on "is a driver assigned" would ask too early and, if the server
 * ever relaxed, would leak the identity of a partner who never confirmed.
 */
const DRIVER_DETAILS_STATUSES: BookingStatus[] = ['ACCEPTED', 'IN_PROGRESS', 'COMPLETED'];

/**
 * How far past the server's own deadline the client waits before giving up
 * on its own.
 * <p>
 * The server stops at its deadline plus at most one sweep interval, because
 * dispatch refuses to start a round it cannot finish inside the budget. This
 * has to comfortably clear that, plus a missed poll and the round trip, so
 * the server's answer is what a rider normally sees and this never
 * pre-empts it.
 */
const CLIENT_GRACE_SECONDS = 15;

/**
 * What a waiting rider is told, and when.
 * <p>
 * Not decoration. Ninety seconds of an unchanging spinner reads as a frozen
 * app long before it reads as a search, and the rider closes it and books a
 * different service. Copy that moves tells her something is still
 * happening. Every line here is also true of what dispatch is actually
 * doing at that moment: the radius really does expand between rounds, which
 * is why "Expanding the search area" is not a placating lie.
 */
// Copy for each stage lives in the translations under tracking.search.<key>.
const SEARCH_STAGES: { afterSeconds: number; key: string }[] = [
  { afterSeconds: 0, key: 'searching' },
  { afterSeconds: 15, key: 'stillSearching' },
  { afterSeconds: 35, key: 'expanding' },
  { afterSeconds: 60, key: 'stillLooking' },
];

function searchStage(seconds: number) {
  return SEARCH_STAGES.reduce((chosen, stage) => (seconds >= stage.afterSeconds ? stage : chosen), SEARCH_STAGES[0]);
}

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
 * REAL, and GATED: the partner's name, photo, vehicle and rating, from
 * GET /api/v1/dispatch/bookings/{id}/driver. They were placeholders until
 * that endpoint existed.
 * <p>
 * They appear only from ACCEPTED onwards, never during MATCHED. MATCHED
 * means a partner has claimed the booking but has not confirmed she is
 * coming, and releasing her name, face, vehicle and registration number at
 * that point would hand a rider the identity of somebody who may never
 * arrive - for every partner the booking touched on its way to being
 * accepted. The server enforces it; this screen additionally does not ask,
 * so nothing leaks even transiently.
 * <p>
 * MOCK still: the ETA and distance strip. Nothing computes either.
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
  const { t } = useTranslation();
  const [shareNote, setShareNote] = useState<string | null>(null);
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  /** Set by the payment card once the fare is captured; the rating prompt waits for it. */
  const [tripPaid, setTripPaid] = useState(false);
  const [askingWhy, setAskingWhy] = useState(false);
  const [driverLocation, setDriverLocation] = useState<DriverLocation | null>(null);
  // Fetched from the chat endpoint, which serves it alongside the thread.
  // Null keeps the support button off the screen rather than offering a
  // button that dials nothing.
  const [supportPhoneNumber, setSupportPhoneNumber] = useState<string | null>(null);
  // Everything the rider is allowed to know about her partner. Null until
  // the server releases it, which it does only from ACCEPTED onwards.
  const [driver, setDriver] = useState<AssignedDriver | null>(null);
  // Seconds this screen has watched the search run. Drives both the changing
  // copy and the defensive fallback below.
  const [searchedSeconds, setSearchedSeconds] = useState(0);
  const [searchTimeoutSeconds, setSearchTimeoutSeconds] = useState(DEFAULT_SEARCH_TIMEOUT_SECONDS);
  const [rebooking, setRebooking] = useState(false);
  // The code she reads out before getting in. Null except while ACCEPTED.
  const [pickupCode, setPickupCode] = useState<string | null>(null);

  /** Set once the trip reaches a status that can never change again. */
  const terminalStatus =
    booking?.status === 'CANCELLED'
    || booking?.status === 'COMPLETED'
    || booking?.status === 'NO_DRIVERS_AVAILABLE';

  /** The search ran and found nobody. Not a cancellation, and not still running. */
  const noDrivers = booking?.status === 'NO_DRIVERS_AVAILABLE';

  /**
   * The client's own giving-up point, and a fallback only.
   * <p>
   * The backend deciding the search is over, and saying so in the booking's
   * status, is the real mechanism. This exists for the case where that
   * answer never arrives - a dropped poll, a backend restart mid-search, a
   * phone that slept through the transition. Without it, any one of those
   * leaves a rider watching a spinner with no end, which is the exact
   * failure this whole change is about.
   * <p>
   * Deliberately later than the server's deadline, never earlier. Firing
   * first would tell a rider nobody was found while a driver was still
   * being offered her trip, and she would book again on top of a search
   * that was about to succeed.
   */
  const clientGaveUp = !booking || terminalStatus
    ? false
    : booking.status === 'REQUESTED' && searchedSeconds > searchTimeoutSeconds + CLIENT_GRACE_SECONDS;

  // The real search budget, so the fallback below sits just behind the
  // server's own deadline rather than at a number guessed in this file.
  useEffect(() => {
    let cancelled = false;
    dispatchApi
      .getSearchConfig()
      .then((config) => {
        if (!cancelled && config.searchTimeoutSeconds > 0) {
          setSearchTimeoutSeconds(config.searchTimeoutSeconds);
        }
      })
      .catch(() => {
        // Keeps the compiled-in default, which matches the backend's. A
        // failed config fetch must not be the thing that decides how long
        // somebody waits.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Ticks only while a search is genuinely running. Measured from when this
  // screen started watching, not from requestedAt, so reopening the app
  // mid-search does not immediately jump to "still looking, hang on".
  useEffect(() => {
    if (booking?.status !== 'REQUESTED') {
      setSearchedSeconds(0);
      return;
    }
    const started = Date.now();
    const tick = setInterval(() => {
      setSearchedSeconds(Math.floor((Date.now() - started) / 1000));
    }, 1000);
    return () => clearInterval(tick);
  }, [booking?.status]);

  useEffect(() => {
    // Stops the moment the trip is finished OR the client has given up. The
    // second half matters: without it, a booking stuck in REQUESTED because
    // the backend never answered would be polled every three seconds for as
    // long as the screen stayed open.
    if (!bookingId || terminalStatus || clientGaveUp) return;
    let cancelled = false;

    async function poll() {
      try {
        const result = await bookingApi.getById(bookingId!);
        if (!cancelled) setBooking(result);
      } catch (err) {
        if (!cancelled) setError(apiErrorText(err, 'tracking.loadError'));
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
  }, [bookingId, terminalStatus, clientGaveUp]);

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

  /**
   * Fetched only once the booking is genuinely ACCEPTED.
   * <p>
   * Keyed on the status, not on driverId. A driverId appears at MATCHED -
   * the moment a partner claims the booking, before she has confirmed she
   * is coming - and asking then would be asking for the identity of
   * somebody who may never arrive. The server refuses it either way; this
   * is the client not asking a question it has no business asking.
   * <p>
   * Cleared whenever the status is not one that releases details, so a
   * partner's name can never linger on screen from a previous render.
   */
  /**
   * The four digits she reads out at the kerb.
   * <p>
   * Fetched only while the booking is ACCEPTED, which is the only window in
   * which it exists and the only one in which it is any use: before that
   * nobody is coming, and after it the trip has already started. Its own
   * call, not a field on the booking, because the booking is served to her
   * partner too - see the endpoint's own note.
   * <p>
   * Cleared whenever the status is not ACCEPTED, so a code can never linger
   * on screen into a trip that has already begun.
   */
  useEffect(() => {
    if (!bookingId || booking?.status !== 'ACCEPTED') {
      setPickupCode(null);
      return;
    }
    let cancelled = false;
    bookingApi
      .getPickupCode(bookingId)
      .then((result) => {
        if (!cancelled) setPickupCode(result.pickupCode);
      })
      .catch(() => {
        // A 404 is the normal answer for a booking that predates pickup
        // verification. The card simply does not appear, and her partner's
        // app lets that trip start without a code - see the backend.
      });
    return () => {
      cancelled = true;
    };
  }, [bookingId, booking?.status]);

  useEffect(() => {
    if (!bookingId || !DRIVER_DETAILS_STATUSES.includes(booking?.status as BookingStatus)) {
      setDriver(null);
      return;
    }
    let cancelled = false;
    dispatchApi
      .getAssignedDriver(bookingId)
      .then((assigned) => {
        if (!cancelled) setDriver(assigned);
      })
      .catch(() => {
        // A 404 here is the server declining to release details, which is
        // a normal state, not a failure. The card keeps its placeholder.
      });
    return () => {
      cancelled = true;
    };
  }, [bookingId, booking?.status]);

  /**
   * Books the same trip again, as a genuinely new booking.
   * <p>
   * A fresh record, not a revival of this one, and the backend enforces that
   * by making NO_DRIVERS_AVAILABLE a dead end. The pickup and drop are
   * reused because retyping them would be absurd, but they are re-submitted
   * and re-priced rather than resurrected: the fare is recalculated, the
   * service-area check runs again, and the new booking gets its own search
   * with its own full budget. Reusing the old record would leave it
   * ambiguous whether "trying again" was working from addresses that were
   * accurate ninety seconds ago.
   * <p>
   * The old booking is deliberately NOT cancelled first, in the one case
   * where it is still technically REQUESTED (the client fallback fired
   * before the server's answer arrived). Cancelling it would put a
   * cancellation on her record for a search she did not abandon - the exact
   * unfairness NO_DRIVERS_AVAILABLE exists to prevent - and it is not
   * needed: dispatch's own deadline has already passed, so the sweeper
   * settles that booking on its own within a tick or two.
   */
  /**
   * Share the trip with someone who should know where she is - the partner's
   * name and registration, where she is going, and a map link to the
   * partner's live position (or the pickup, before anyone is assigned).
   * Through the phone's own share sheet; nothing is published and there is no
   * public trip page. This replaced a button that showed a "not available"
   * message.
   */
  async function shareTrip() {
    if (!booking) return;
    const text = driver
      ? t('tracking.shareText', {
          name: driver.name ?? t('tracking.yourPartner'),
          vehicle: driver.vehicleRegistrationNumber ? ` (${driver.vehicleRegistrationNumber})` : '',
          drop: booking.drop.label,
          link: driverLocation ? mapsLink(driverLocation.lat, driverLocation.lng) : mapsLink(booking.pickup.lat, booking.pickup.lng),
        })
      : t('tracking.shareTextNoDriver', { drop: booking.drop.label, link: mapsLink(booking.pickup.lat, booking.pickup.lng) });
    const outcome = await shareViaDevice(t('tracking.shareTrip'), text, '');
    if (outcome === 'unsupported') {
      try {
        await navigator.clipboard?.writeText(text);
        setShareNote(t('tracking.shareCopied'));
      } catch {
        setShareNote(text);
      }
    }
  }

  async function handleTryAgain() {
    if (!booking) return;
    setRebooking(true);
    setError(null);
    try {
      const fresh = await bookingApi.create({
        type: booking.type,
        category: booking.category,
        pickup: booking.pickup,
        drop: booking.drop,
      });
      // replace, not push: the failed booking should not be a back-button
      // away from a trip that is now live.
      navigate(`/tracking/${fresh.id}`, { replace: true });
    } catch (err) {
      setError(apiErrorText(err, 'tracking.rebookError'));
    } finally {
      setRebooking(false);
    }
  }

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
      setCancelError(apiErrorText(err, 'tracking.cancelError'));
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
  // The client giving up counts as finished for rendering: the screen must
  // stop claiming to search either way. The booking itself is untouched by
  // that, which is why Try Again still works from here.
  const isFinished = terminalStatus || clientGaveUp;
  /** Ended by the partner, not yet paid - not complete. */
  const paymentDue = booking?.status === 'COMPLETED' && !tripPaid && !booking.paymentSettledAt;
  const searchFailed = noDrivers || clientGaveUp;
  /**
   * A partner has confirmed she is coming.
   * <p>
   * Keyed on the status, not on driverId. A driverId is set at MATCHED, the
   * moment somebody claims the booking, and this used to key on that - so
   * the header read "On the Way" about a partner who had not agreed to come
   * and might never. Saying it early is the same overclaim the details gate
   * exists to prevent, just in the title bar.
   */
  const hasDriver = Boolean(booking?.driverId)
    && !isFinished
    && DRIVER_DETAILS_STATUSES.includes(booking?.status as BookingStatus);
  const markers: MapMarker[] = [];
  if (booking) {
    markers.push({ key: 'pickup', lat: booking.pickup.lat, lng: booking.pickup.lng, label: t('booking.pickup'), kind: 'pickup' });
    markers.push({ key: 'drop', lat: booking.drop.lat, lng: booking.drop.lng, label: t('booking.drop'), kind: 'drop' });
  }
  if (driverLocation) {
    markers.push({ key: 'driver', lat: driverLocation.lat, lng: driverLocation.lng, label: t('tracking.driver'), kind: 'driver' });
  }
  // Unchanged for every normal case: cancelling during a live search still
  // works exactly as it did, reason dialog and all. Only suppressed once the
  // failure card is up, which carries its own way out - two Cancel buttons
  // on one screen, meaning different things, is worse than either.
  const canCancel =
    booking && !searchFailed && ['REQUESTED', 'MATCHED', 'ACCEPTED'].includes(booking.status);

  return (
    <div className="space-y-6">
      <TopHeader
        variant="back"
        title={
          searchFailed
            ? t('tracking.header.noDrivers')
            : isFinished
              ? (booking?.status === 'CANCELLED' ? t('tracking.header.cancelled') : paymentDue ? t('tracking.header.paymentDue') : t('tracking.header.completed'))
              : hasDriver
                ? t('tracking.header.onTheWay')
                : t('tracking.header.finding')
        }
        onBack={() => navigate('/home')}
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      {!booking ? (
        <SkeletonCard lines={4} label={t('tracking.loading')} />
      ) : searchFailed ? (
        /* The search is over and found nobody. Two ways forward and no
           spinner - which is the entire point of the status existing. */
        <Card tone="warning" className="flex items-start gap-3">
          <IconCircle size="lg" tone="soft" color="orange" icon={<SearchX />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">{t('tracking.noDriversTitle')}</p>
            <p className="mt-1 text-sm text-text-secondary">
              {t('tracking.noDriversBody', { place: booking.pickup.label })}
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="md" disabled={rebooking} onClick={handleTryAgain}>
                {rebooking ? t('booking.booking') : t('tracking.tryAgain')}
              </Button>
              <Button size="md" variant="secondary" disabled={rebooking} onClick={() => navigate('/home')}>
                {t('common.cancel')}
              </Button>
            </div>
          </div>
        </Card>
      ) : isFinished && paymentDue ? (
        /* Arrived, not finished. The trip is over only once it is paid, so
           this does not say "completed" or draw a success tick over a fare
           still owed - the payment card below is the job in front of her. */
        <Card tone="warning" className="flex items-start gap-3" data-testid="trip-payment-due-banner">
          <IconCircle size="lg" tone="soft" color="orange" icon={<WalletIcon />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">{t('tracking.arrivedTitle')}</p>
            <p className="mt-1 text-sm text-text-secondary">
              {t('tracking.arrivedBody')}
            </p>
          </div>
        </Card>
      ) : isFinished ? (
        <Card
          tone={booking.status === 'CANCELLED' ? 'danger' : 'success'}
          className="flex items-start gap-3"
          data-testid={booking.status === 'COMPLETED' ? 'trip-complete' : undefined}
        >
          {booking.status === 'CANCELLED' ? (
            <IconCircle size="lg" tone="soft" color="red" icon={<XCircle />} />
          ) : (
            // Drawn once when the trip is paid - not confetti: this is the
            // end of an ordinary journey, and the same screen shows after one
            // that went badly.
            <SuccessCheck size={48} label={t('tracking.header.completed')} />
          )}
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">
              {booking.status === 'CANCELLED' ? t('tracking.cancelledTitle') : t('tracking.completedTitle')}
            </p>
            <p className="mt-1 text-sm text-text-secondary">
              {booking.status === 'CANCELLED'
                ? t('tracking.cancelledBody')
                : t('tracking.completedBody')}
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="md" variant="secondary" onClick={() => navigate('/home')}>
                {booking.status === 'CANCELLED' ? t('tracking.bookAnother') : t('tracking.backHome')}
              </Button>
              {/* The thread is read-only now, but it is not gone. If there is
                  ever a disagreement about what was agreed on this trip, it
                  is the only account of it either side has - so it stays
                  reachable after the trip, not only during it. */}
              {booking.driverId && (
                <Button size="md" variant="secondary" onClick={() => navigate(`/chat/${booking.id}`)}>
                  {t('tracking.viewMessages')}
                </Button>
              )}
            </div>
          </div>
        </Card>
      ) : !hasDriver ? (
        <Card className="text-center">
          {/* Copy that moves with the clock. A static line for ninety
              seconds reads as a frozen app, and a rider who thinks the app
              has hung closes it and books something else - so this is about
              keeping her informed, not about filling the silence. Every
              stage matches what dispatch is really doing; see SEARCH_STAGES. */}
          {/* MATCHED gets its own line. The staged search copy would say
              "Searching for a nearby driver" directly above a badge reading
              "Partner assigned", which contradicts itself. This says what is
              actually happening - somebody has the request and is deciding -
              without naming her, because she has not agreed to come yet. */}
          <p className="font-heading font-semibold text-text-primary">
            {booking.status === 'MATCHED' ? t('tracking.confirming') : t(`tracking.search.${searchStage(searchedSeconds).key}.title`)}
          </p>
          <p className="mt-1 text-sm text-text-secondary">
            {booking.status === 'MATCHED'
              ? t('tracking.confirmingBody')
              : t(`tracking.search.${searchStage(searchedSeconds).key}.detail`)}
          </p>

          {/* A real bar against the real budget, so the wait has a visible
              end. Capped at 100% rather than allowed to overflow while the
              client waits out its grace period. */}
          <div
            className="mt-4 h-1.5 w-full overflow-hidden rounded-full bg-background"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={searchTimeoutSeconds}
            aria-valuenow={Math.min(searchedSeconds, searchTimeoutSeconds)}
            aria-label={t('tracking.searchProgress')}
          >
            <div
              className="h-full rounded-full bg-primary transition-[width] duration-1000 ease-linear"
              style={{ width: `${Math.min(100, (searchedSeconds / searchTimeoutSeconds) * 100)}%` }}
            />
          </div>

          <StatusBadge tone="warning" className="mt-3">
            {bookingStatusLabel(booking.status)}
          </StatusBadge>
        </Card>
      ) : (
        <Card className="flex items-center gap-3">
          {/* Her real photo, or a silhouette. Never a broken image - this is
              the screen where a rider checks the person in front of her is
              the one the app sent. */}
          <Avatar url={driver?.photoUrl} name={driver?.name} size="lg" />
          <div className="min-w-0 flex-1">
            {/* All real now, and released by the server only once she has
                accepted. Nothing here is rendered during MATCHED: the
                request is not even made - see DRIVER_DETAILS_STATUSES. */}
            <p className="truncate font-heading font-semibold text-text-primary">
              {driver?.name || t('tracking.yourPartner')}
            </p>
            {/* Rendered only once the server has actually released her
                details. Showing "New partner" while waiting would assert
                something about a specific person - that nobody has rated her
                - on no information at all. */}
            {driver && (
              <p className="truncate text-xs text-text-secondary">
                <AggregateRatingText
                  averageStars={driver.averageStars}
                  totalRatings={driver.totalRatings}
                  emptyLabel={t('tracking.newPartner')}
                />
                {driver.vehicleType && <> &middot; {vehicleLabel(driver.vehicleType)}</>}
              </p>
            )}
            {driver?.vehicleRegistrationNumber && (
              // The number to look for, set apart rather than buried in the
              // line above: at night, at a kerb, it is the thing she is
              // actually checking.
              <p className="mt-1 inline-block rounded bg-background px-2 py-0.5 font-heading text-sm font-semibold tracking-wide text-text-primary">
                {driver.vehicleRegistrationNumber}
              </p>
            )}
          </div>
          {/* The one way to reach her. Not a call, and not a number. */}
          <button
            aria-label={t('tracking.messagePartner')}
            className="rounded-full p-3 text-primary hover:bg-background"
            onClick={() => navigate(`/chat/${bookingId}`)}
          >
            <MessageCircle className="h-5 w-5" />
          </button>
        </Card>
      )}

      {/* The one thing on this screen she has to DO, so it sits directly
          under the partner it belongs to and above the map. It disappears
          the moment the trip starts, because by then it has been used. */}
      <PickupCodeCard code={booking?.status === 'ACCEPTED' ? pickupCode : null} />

      {/* Which half of the trip is happening, said plainly. "Partner
          assigned" and "trip underway" are very different facts to a woman
          watching a marker move, and the status badge alone was carrying
          both. */}
      {(booking?.status === 'ACCEPTED' || booking?.status === 'IN_PROGRESS') && (
        <Card className="flex items-start gap-3">
          <IconCircle
            tone="soft"
            color={booking?.status === 'IN_PROGRESS' ? 'green' : undefined}
            icon={booking?.status === 'IN_PROGRESS' ? <Navigation /> : <Radio />}
          />
          <div className="min-w-0 flex-1">
            <p className="font-heading font-semibold text-text-primary">
              {booking?.status === 'IN_PROGRESS' ? t('tracking.onYourWay') : t('tracking.comingToCollect')}
            </p>
            <p className="mt-1 text-sm text-text-secondary">
              {booking?.status === 'IN_PROGRESS'
                ? t('tracking.onYourWayBody')
                : <SafetyText k="pickupCode.haveReady" />}
            </p>
          </div>
        </Card>
      )}

      <div className="space-y-1">
        <LiveMap markers={markers} />
        <p className="text-xs text-text-secondary">
          {isFinished
            ? t('tracking.mapFinished')
            : driverLocation
              ? t('tracking.mapUpdated', { seconds: secondsAgo(driverLocation.recordedAt), every: DRIVER_LOCATION_POLL_MS / 1000 })
              : hasDriver
                ? t('tracking.mapWaiting')
                : t('tracking.mapPreview')}
        </p>
        {/* Her partner's last reported position, in the map she already
            knows how to read. A small Leaflet view in a card is fine for a
            glance; somebody trying to work out which side of a flyover a
            bike is on wants to pinch and zoom in something familiar.
            Offered only while a trip is live and a real position exists -
            never a stale point from a finished trip. */}
        {!isFinished && driverLocation && (
          <OpenInMapsButton
            lat={driverLocation.lat}
            lng={driverLocation.lng}
            label={t('tracking.yourPartner')}
            variant="secondary"
            className="mt-2"
          >
            {t('tracking.openInMaps')}
          </OpenInMapsButton>
        )}
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {/* REAL: fareEstimate comes back from bookingApi.create() at RideBooking/
          DeliveryBooking time (RequestBookingCommand runs FareCalculator
          immediately, there's no separate quote step - see those screens'
          file comments) - this is the first place it's actually shown.
          Sits under the map, where the mockup puts its arriving/distance
          strip - this app has no ETA to show there. */}
      {/* A completed trip shows what is owed and how to pay it; the payment
          card carries the final fare, so the plain fare row would repeat it. */}
      {booking?.status === 'COMPLETED' && <TripPaymentCard booking={booking} onPaid={() => setTripPaid(true)} />}

      {booking && booking.status !== 'COMPLETED' && (
        <Card className="flex items-center justify-between">
          {/* A cancelled trip was never charged. Showing a rupee figure
              with no qualifier reads as a bill. */}
          <span className="text-sm text-text-secondary">
            {booking.status === 'CANCELLED' || searchFailed ? t('tracking.fareNotCharged') : t('fare.estimated')}
          </span>
          <AmountText amount={booking.finalFare ?? booking.fareEstimate} size="lg" />
        </Card>
      )}

      {/* How far away she is, from her last real position - to the pickup
          while she is coming, to the drop once the trip has started. This
          used to be a placeholder reading "-- min (mock)". Straight-line
          distance and a city-traffic speed: an honest "about", labelled as
          one, rather than a routed ETA the app does not have. */}
      {booking && hasDriver && driverLocation && (booking.status === 'ACCEPTED' || booking.status === 'IN_PROGRESS') && (() => {
        const target = booking.status === 'IN_PROGRESS' ? booking.drop : booking.pickup;
        const km = straightLineKm(driverLocation, target);
        const minutes = Math.max(1, Math.round((km / CITY_SPEED_KMH) * 60));
        return (
          <Card data-testid="trip-eta">
            <div className="flex justify-between text-sm">
              <div>
                <p className="text-text-secondary">
                  {booking.status === 'IN_PROGRESS' ? t('tracking.etaToDrop') : t('tracking.etaToPickup')}
                </p>
                <p className="font-heading font-semibold text-text-primary">{t('tracking.aboutMinutes', { count: minutes })}</p>
              </div>
              <div className="text-right">
                <p className="text-text-secondary">{t('tracking.distance')}</p>
                <p className="font-heading font-semibold text-text-primary">{t('fare.approxKm', { km: km.toFixed(1) })}</p>
              </div>
            </div>
          </Card>
        );
      })()}

      {/* Hidden once the trip is over. Sharing a live location for a
          cancelled trip, or offering to message a driver who was never
          coming, are both offers of something that does not exist. SOS stays
          reachable from the tab bar and Home regardless. */}
      {!isFinished && (
      <div className="flex justify-around">
        <button
          className="flex flex-col items-center gap-1 text-xs text-text-secondary"
          onClick={shareTrip}
          data-testid="share-trip"
        >
          <IconCircle tone="soft" icon={<Radio />} />
          {t('tracking.shareTrip')}
        </button>
        <button
          className="flex flex-col items-center gap-1 text-xs text-danger"
          onClick={() => navigate('/sos', { state: { bookingId } })}
        >
          <IconCircle color="red" tone="soft" icon={<ShieldAlert />} />
          {t('home.sos')}
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
            {t('tracking.support')}
          </button>
        ) : (
          <button
            className="flex flex-col items-center gap-1 text-xs text-text-secondary"
            onClick={() => navigate(`/chat/${bookingId}`)}
          >
            <IconCircle tone="soft" icon={<MessageCircle />} />
            {t('chat.title')}
          </button>
        )}
      </div>
      )}

      {shareNote && <p className="text-center text-xs text-text-secondary" data-testid="share-note">{shareNote}</p>}

      {canCancel && (
        <Button variant="danger" fullWidth disabled={cancelling} onClick={() => { setCancelError(null); setAskingWhy(true); }}>
          {t('tracking.cancelRide')}
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
      {/* And only once the fare is settled: asked the moment the trip ends,
          the rating dialog sat on top of the payment card and the rider had
          to deal with it before she could pay. My Bookings still offers to
          rate a trip that is left unpaid here. */}
      {booking?.status === 'COMPLETED' && tripPaid && (
        <RatingPrompt
          bookingId={bookingId}
          counterpartLabel={t('common.yourPartner')}
          counterpartName={driver?.name ?? null}
          counterpartPhotoUrl={driver?.photoUrl ?? null}
          tripSummary={booking ? `${booking.pickup.label} → ${booking.drop.label}` : null}
        />
      )}
    </div>
  );
}

/** Whole seconds since an ISO timestamp, floored at 0 for clock skew. */
function secondsAgo(iso: string): number {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
}

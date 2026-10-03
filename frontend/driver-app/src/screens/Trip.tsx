import { ArrowLeft, CheckCircle2, ChevronDown, MapPin, MessageCircle, Navigation, UserX } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  AggregateRatingText,
  Avatar,
  Button,
  CancelReasonDialog,
  Card,
  ConfirmDialog,
  ContactSupportButton,
  HelmetIcon,
  DRIVER_CANCELLATION_REASONS,
  DROP_OFF_REASONS,
  IconCircle,
  LiveMap,
  OpenInMapsButton,
  PaymentSuccessFlash,
  PICKUP_CODE_LENGTH,
  PickupCodeField,
  SafetyText,
  SkeletonCard,
  StatusBadge,
  SuccessCheck,
  TopHeader,
  bookingStatusLabel,
} from '@sheout/design-system';
import type { CancellationReason, DropOffReason, MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi, chatApi, dispatchApi, type AssignedRider } from '../api/client';
import { TripInsuranceActions } from '../components/TripInsuranceActions';
import { apiErrorText } from '../lib/apiErrors';
import type { BookingSummary, PaymentHold, PaymentSummary, TripRoute } from '../api/types';
import { CollectPaymentCard } from '../components/CollectPaymentCard';
import { readPositionOnce, useShareLocation } from '../lib/LocationBroadcastContext';
import { PartnerSos } from '../components/PartnerSos';
import { DestinationChangePrompt } from '../components/DestinationChangePrompt';
import { NavigationView } from '../components/NavigationView';
import { useNavigation } from '../lib/navigation';
import { useTranslation } from '@sheout/design-system';

const POLL_INTERVAL_MS = 4000;
const JUST_ENDED_MS = 15 * 60 * 1000;

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString([], { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

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

/**
 * Off the line by more than this (or 1.5x her GPS accuracy, if worse), on
 * two fixes running, and she has left the route: it is drawn again from
 * where she is. One wobbly fix is not a wrong turn.
 */
const OFF_ROUTE_METRES = 40;
/** Never re-route more often than this, whatever the GPS says. */
const REROUTE_MIN_MS = 15000;
/** Navigation closes on arrival within this much road of the destination. */
const ARRIVED_ROAD_METRES = 100;

const EARTH_RADIUS_M = 6371000;
// UX hint only - the backend decides (TRIP_COMPLETION_RADIUS_METRES). Away
// from the drop she can still end the trip; she is asked why first.
const DROP_OFF_GEOFENCE_METRES = 150;

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
  const { t } = useTranslation();
  const { t: ds } = useTranslation('ds');
  /** Her rider's first name, photo and rating - released by the server from ACCEPTED onwards. */
  const [rider, setRider] = useState<AssignedRider | null>(null);
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  /**
   * The last poll failed. Kept apart from `error` and cleared by the next
   * poll that works: a single dropped request on a weak signal used to leave
   * "Could not load trip" on screen for the rest of the trip, above a trip
   * that had loaded perfectly well.
   */
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [askingWhy, setAskingWhy] = useState(false);
  /** Ending away from the drop: the reason picker is open. */
  const [askingDropReason, setAskingDropReason] = useState(false);
  const [dropReasonError, setDropReasonError] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [supportPhoneNumber, setSupportPhoneNumber] = useState<string | null>(null);
  /** Paid, as far as this screen knows - from the booking, then from the payment card's polling. */
  const [paid, setPaid] = useState(false);
  /** The hold keeping new offers away while this trip is unpaid; null once it has lifted. */
  const [hold, setHold] = useState<PaymentHold | null | undefined>(undefined);

  /**
   * She has looked at her rider and said "yes, it's her" - kept for this
   * booking across a refresh, so she is not asked twice at the same kerb.
   */
  const [riderConfirmed, setRiderConfirmed] = useState(() => {
    try {
      return sessionStorage.getItem(`sheout_rider_confirmed_${bookingId}`) === '1';
    } catch {
      return false;
    }
  });
  const [askingMismatch, setAskingMismatch] = useState(false);
  const [mismatchError, setMismatchError] = useState<string | null>(null);
  /** The fare has just landed - the "payment received" moment, once. */
  const [paidFlash, setPaidFlash] = useState<PaymentSummary | null>(null);

  const [pickupCode, setPickupCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);
  /** Set once the server says the attempt limit is spent. The keypad closes. */
  const [codeLocked, setCodeLocked] = useState(false);
  const [pickupArrived, setPickupArrived] = useState(false);

  const [route, setRoute] = useState<TripRoute | null>(null);
  const [routeError, setRouteError] = useState(false);
  // Where she was when the current line was drawn, so the next fix can be
  // measured against it rather than re-routing on every one.
  const routedFrom = useRef<{ lat: number; lng: number } | null>(null);
  const routeInFlight = useRef(false);
  const lastRouteAt = useRef(0);
  /** Bumped at each phase change, so a route fetched for the phase before is dropped. */
  const routeGeneration = useRef(0);
  /** Fixes in a row off the line: two, and she is re-routed. */
  const offRouteFixes = useRef(0);
  const [rerouting, setRerouting] = useState(false);
  /** Failed route fetches in a row this phase. One is a blip and is not shown. */
  const routeFailures = useRef(0);
  /** Set by the retry timer: fetch again at the next chance, moved or not. */
  const routeRetryDue = useRef(false);
  const [routeRetryTick, setRouteRetryTick] = useState(0);

  // Live for the whole trip, MATCHED included. The subscription itself
  // lives above the router so it is not dropped on the way in from Home or
  // the offer screen - see LocationBroadcastContext.
  const tripIsLive = booking
    ? ['MATCHED', 'ACCEPTED', 'IN_PROGRESS'].includes(booking.status)
    : false;
  const location = useShareLocation(tripIsLive);
  const myPosition = location.position;
  const atDropOff = Boolean(
    booking?.status === 'IN_PROGRESS'
      && myPosition
      && booking.drop
      && metresBetween(myPosition, booking.drop) <= DROP_OFF_GEOFENCE_METRES
  );

  const phase = booking?.status === 'IN_PROGRESS' ? 'DROP' : 'PICKUP';
  const settled = paid || Boolean(booking?.paymentSettledAt);

  // Who she is collecting, once the server will say - from ACCEPTED on.
  const riderVisible = booking?.status === 'ACCEPTED' || booking?.status === 'IN_PROGRESS' || booking?.status === 'COMPLETED';
  useEffect(() => {
    if (!riderVisible || !bookingId || rider) return;
    dispatchApi.getAssignedRider(bookingId).then(setRider).catch(() => undefined);
  }, [riderVisible, bookingId, rider]);
  const awaitingPayment = booking?.status === 'COMPLETED' && !settled;

  useEffect(() => {
    if (!bookingId || booking?.status !== 'ACCEPTED') {
      setPickupArrived(false);
      return;
    }
    let cancelled = false;
    const check = () => {
      bookingApi.pickupStatus(bookingId)
        .then((result) => {
          if (!cancelled) setPickupArrived(result.arrived);
        })
        .catch(() => {
          if (!cancelled) setPickupArrived(false);
        });
    };
    check();
    const timer = window.setInterval(check, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [bookingId, booking?.status]);

  // Over, either way. From here the screen is the trip's record - opened from
  // My Bookings or a notification - not a job to do.
  const finished = booking?.status === 'COMPLETED' || booking?.status === 'CANCELLED';
  const endedAt = booking?.completedAt ?? booking?.cancelledAt ?? null;
  // The check drawing itself and "Done" belong to the moment a trip ends,
  // not to every later look at it from history.
  const justEnded = endedAt != null && Date.now() - new Date(endedAt).getTime() < JUST_ENDED_MS;

  /** Back to wherever she came from - the list or the inbox - and to My Bookings on a cold open. */
  function goBack() {
    if (finished && (window.history.state?.idx ?? 0) > 0) navigate(-1);
    else navigate(finished ? '/bookings' : '/home');
  }

  // While the fare is outstanding, keep asking whether the hold on new
  // offers still stands, so the screen can tell her the moment it lifts.
  useEffect(() => {
    if (!awaitingPayment) return;
    let cancelled = false;
    const load = () =>
      bookingApi
        .getPaymentHold()
        .then((h) => {
          if (!cancelled) setHold(h && h.bookingId === bookingId ? h : null);
        })
        .catch(() => undefined);
    load();
    const timer = window.setInterval(load, 15000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [awaitingPayment, bookingId]);

  useEffect(() => {
    if (!bookingId) return;
    let cancelled = false;
    let interval: ReturnType<typeof setInterval> | undefined;

    async function poll() {
      try {
        const result = await bookingApi.getById(bookingId!);
        if (cancelled) return;
        setBooking(result);
        setLoadError(null);
        // A record, not a live trip: nothing on it will change again.
        if (result.status === 'CANCELLED' || (result.status === 'COMPLETED' && result.paymentSettledAt)) {
          clearInterval(interval);
        }
      } catch (err) {
        if (!cancelled) setLoadError(apiErrorText(err, 'trip.loadError'));
      }
    }

    interval = setInterval(poll, POLL_INTERVAL_MS);
    poll();
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
      if (!bookingId || routeInFlight.current) return;
      routeInFlight.current = true;
      lastRouteAt.current = Date.now();
      // Where this attempt was made from, success or not, so a failure is
      // retried after she has moved on rather than on every fix.
      routedFrom.current = from;
      const generation = routeGeneration.current;
      // A failed fetch used to be retried only once she had moved 300 m, so
      // a single blip while she stood at the pickup (or had just set off)
      // left "Could not draw the road" up for minutes. Now it is retried on
      // a timer - 5 s, 10 s, 20 s ... at most once a minute, well inside the
      // server's 8 a minute - and only a second failure in a row is shown.
      const failed = () => {
        if (generation !== routeGeneration.current) return;
        routeFailures.current += 1;
        setRouteError(routeFailures.current >= 2);
        const delay = Math.min(5000 * 2 ** (routeFailures.current - 1), 60000);
        window.setTimeout(() => {
          if (generation !== routeGeneration.current) return;
          routeRetryDue.current = true;
          setRouteRetryTick((n) => n + 1);
        }, delay);
      };
      try {
        const fetched = await bookingApi.getRoute(bookingId, from);
        // A route for the phase that has just ended (fetched to the pickup,
        // arriving after the code was accepted) is dropped, never drawn.
        if (generation !== routeGeneration.current) return;
        if (fetched.points.length) {
          routeFailures.current = 0;
          setRoute(fetched);
          setRouteError(false);
        } else {
          // Keep any line already drawn: an empty answer to a re-route is no
          // reason to take away the road she is following.
          setRoute((current) => current ?? fetched);
          failed();
        }
      } catch {
        // A route is a convenience, not the trip. The destination marker and
        // the Google Maps button both still work without it, so this is
        // reported rather than treated as a screen error.
        failed();
      } finally {
        routeInFlight.current = false;
        setRerouting(false);
      }
    },
    [bookingId]
  );

  // Re-route on a phase change, and drop the old line immediately so a route
  // to the pickup is never left on screen after the trip has started. The
  // same when the drop itself moves - her rider changed destination and she
  // agreed - so the line to the old drop goes at once.
  useEffect(() => {
    routeGeneration.current += 1;
    setRoute(null);
    setRouteError(false);
    routedFrom.current = null;
    offRouteFixes.current = 0;
    routeFailures.current = 0;
    routeRetryDue.current = false;
  }, [phase, bookingId, booking?.drop.lat, booking?.drop.lng]);

  /** After she answers a change of destination: the new drop and fare now, not at the next poll. */
  function refreshBooking() {
    if (!bookingId) return;
    bookingApi.getById(bookingId).then(setBooking).catch(() => undefined);
  }

  // Where she is along the route: the next turn, what is left, and whether
  // she has left the line.
  const { prepared, state: nav } = useNavigation(route, myPosition);

  // When to fetch the route: once as each phase starts, and again only when
  // she has really left it (see OFF_ROUTE_METRES), at most every 15 seconds.
  // A failed fetch is retried once she has moved on 300 m.
  useEffect(() => {
    if (!myPosition || !booking) return;
    if (booking.status !== 'ACCEPTED' && booking.status !== 'IN_PROGRESS') return;
    const last = routedFrom.current;
    if (!last) {
      loadRoute(myPosition);
      return;
    }
    if (routeError || !route || routeRetryDue.current) {
      if (routeRetryDue.current || metresBetween(last, myPosition) >= ROUTE_REFRESH_METRES) {
        routeRetryDue.current = false;
        loadRoute(myPosition);
      }
      return;
    }
    if (!nav) return;
    const tolerance = Math.max(OFF_ROUTE_METRES, (myPosition.accuracy ?? 0) * 1.5);
    offRouteFixes.current = nav.offRouteMetres > tolerance ? offRouteFixes.current + 1 : 0;
    if (offRouteFixes.current >= 2 && Date.now() - lastRouteAt.current >= REROUTE_MIN_MS) {
      offRouteFixes.current = 0;
      setRerouting(true);
      loadRoute(myPosition);
    }
    // nav is derived from myPosition and route; both are listed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myPosition, booking?.status, route, routeError, loadRoute, routeRetryTick]);

  // In-app navigation opens by itself as each phase starts - on accepting,
  // to the pickup; once the code is accepted, to the drop - the way ride
  // apps do. Once per phase: if she closes it for the trip details, it stays
  // closed until she opens it again or the next phase starts.
  const [navigating, setNavigating] = useState(false);
  const autoNavFor = useRef<string | null>(null);
  const arrivedHere = phase === 'PICKUP' ? pickupArrived : atDropOff;
  const nearDestination = arrivedHere && (!nav || nav.remainingMetres <= ARRIVED_ROAD_METRES);
  useEffect(() => {
    if (!booking || (booking.status !== 'ACCEPTED' && booking.status !== 'IN_PROGRESS')) {
      setNavigating(false);
      return;
    }
    const key = `${booking.id}:${booking.status}`;
    if (autoNavFor.current !== key) {
      autoNavFor.current = key;
      setNavigating(!nearDestination);
    }
  }, [booking?.id, booking?.status, nearDestination]);
  // Arrived: navigation closes and the trip screen shows the next step -
  // the pickup code, or End trip.
  useEffect(() => {
    if (nearDestination) setNavigating(false);
  }, [nearDestination]);

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
      setCancelError(apiErrorText(err, 'trip.cancelError'));
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
  /**
   * Sends where she is right now before a check that reads it. The server
   * trusts a position for thirty seconds, and a phone that has just been
   * unlocked at the kerb may not have sent one for longer than that - so the
   * first tap on Start or End failed with "location too old" for no reason
   * she could see. Best effort: the server still decides.
   */
  function confirmRider() {
    setRiderConfirmed(true);
    try {
      sessionStorage.setItem(`sheout_rider_confirmed_${bookingId}`, '1');
    } catch {
      // Private mode: she may be asked again after a refresh, which is fine.
    }
  }

  /**
   * "This is not my rider." The trip is not started; it is cancelled with a
   * reason that never counts against her, and the rider's account goes to
   * SheOut's safety team. See CancellationReason.IDENTITY_MISMATCH.
   */
  async function handleMismatch() {
    if (!bookingId) return;
    setBusy(true);
    setMismatchError(null);
    try {
      await bookingApi.cancel(bookingId, 'IDENTITY_MISMATCH');
      setAskingMismatch(false);
      navigate('/home', { replace: true });
    } catch (err) {
      setMismatchError(apiErrorText(err, 'trip.cancelError'));
    } finally {
      setBusy(false);
    }
  }

  async function sendFreshPosition() {
    const here = await readPositionOnce(myPosition);
    if (here) await dispatchApi.recordLocation(here.lat, here.lng).catch(() => undefined);
  }

  async function handleConfirmPickup() {
    if (!bookingId || pickupCode.length !== PICKUP_CODE_LENGTH) return;
    setBusy(true);
    setCodeError(null);
    try {
      await sendFreshPosition();
      const updated = await bookingApi.start(bookingId, pickupCode);
      setBooking(updated);
      setPickupCode('');
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'PICKUP_VERIFICATION_LOCKED') {
        setCodeLocked(true);
        setCodeError(t('apiError.PICKUP_VERIFICATION_LOCKED'));
      } else {
        setCodeError(apiErrorText(err, 'trip.confirmError'));
        // Cleared so she types four fresh digits rather than editing a
        // wrong code - which is how a second attempt becomes a third.
        setPickupCode('');
      }
    } finally {
      setBusy(false);
    }
  }

  /**
   * Ends the trip. At the drop that is one tap. Away from it - by her own
   * GPS, or because the server says so - she is asked why first; the trip
   * still ends, and the reason goes on the booking.
   */
  async function handleComplete(reason?: DropOffReason, note?: string) {
    if (!bookingId) return;
    if (!reason && myPosition && !atDropOff) {
      setDropReasonError(null);
      setAskingDropReason(true);
      return;
    }
    setBusy(true);
    setError(null);
    setDropReasonError(null);
    try {
      await sendFreshPosition();
      const updated = await bookingApi.complete(bookingId, reason, note);
      setAskingDropReason(false);
      // Stays on this screen: the fare still has to be collected, and the
      // payment card below appears in place of the trip controls.
      setBooking(updated);
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'DROP_OFF_REASON_REQUIRED') {
        setAskingDropReason(true);
      } else if (reason) {
        setDropReasonError(apiErrorText(err, 'trip.completeError'));
      } else {
        setError(apiErrorText(err, 'trip.completeError'));
      }
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
    if (finished) {
      // A record shows the whole trip, where it started and where it went.
      markers.push({ key: 'pickup', lat: booking.pickup.lat, lng: booking.pickup.lng, label: t('trip.pickup'), kind: 'pickup' });
      markers.push({ key: 'drop', lat: booking.drop.lat, lng: booking.drop.lng, label: t('trip.drop'), kind: 'drop' });
    } else if (phase === 'PICKUP') {
      markers.push({ key: 'pickup', lat: booking.pickup.lat, lng: booking.pickup.lng, label: t('trip.pickup'), kind: 'pickup' });
    } else {
      markers.push({ key: 'drop', lat: booking.drop.lat, lng: booking.drop.lng, label: t('trip.drop'), kind: 'drop' });
    }
  }
  if (myPosition && !finished) {
    markers.push({ key: 'me', lat: myPosition.lat, lng: myPosition.lng, label: t('home.you'), kind: 'driver', heading: myPosition.heading });
  }

  const destination = booking ? (phase === 'PICKUP' ? booking.pickup : booking.drop) : null;
  const navigable = booking?.status === 'ACCEPTED' || booking?.status === 'IN_PROGRESS';

  function mapCaption(): string {
    if (finished && booking) return t('trip.requestedOn', { when: formatWhen(booking.requestedAt) });
    if (!navigable) return t('home.positionNote');
    if (routeError) return t('trip.routeError');
    if (route?.distanceKm != null) {
      const minutes = route.durationMinutes == null ? null : Math.round(route.durationMinutes);
      const where = phase === 'PICKUP' ? t('trip.toPickup') : t('trip.toDrop');
      return minutes == null
        ? t('trip.routeKm', { km: route.distanceKm, where })
        : t('trip.routeKmMin', { km: route.distanceKm, minutes, where });
    }
    if (!myPosition) return location.error ?? t('trip.findingLocation');
    return t('trip.workingOutRoute');
  }

  // THE LIVE TRIP: one job at a time. The map is big, because on a bike it
  // is the thing she glances at; under it is a single card saying what to do
  // now - go to the pickup, take the code, go to the drop - with only the
  // controls for that step. The code box is not shown on the way to the
  // pickup, the End button not before the rider is on board, and the whole
  // trip's addresses and fare fold away under "Trip details". Cancel and
  // Support stay reachable but quiet, at the bottom, where a stray thumb
  // does not find them.
  if (booking && navigable && destination) {
    // What is left from where she is, once she is on the line; the whole route before that.
    const minutes = nav
      ? Math.max(1, Math.round(nav.remainingSeconds / 60))
      : route?.durationMinutes == null ? null : Math.max(1, Math.round(route.durationMinutes));
    const kmLeft = nav ? Math.round(nav.remainingMetres / 100) / 10 : route?.distanceKm ?? null;
    const arrived = phase === 'PICKUP' && pickupArrived;
    // At the drop, told so - the same way arriving at the pickup is. Read
    // off her GPS; the server checks again when she ends the trip.
    const atDrop = phase === 'DROP' && atDropOff;
    const title = phase === 'DROP'
      ? (atDrop ? t('trip.arrivedDropTitle') : t('trip.goToDrop'))
      : arrived ? t('trip.arrivedTitle') : t('trip.goToPickup');
    // A parcel's sender hands it over and stays behind: there is nobody to
    // check the face of, and no helmet to give.
    const isRide = booking.type === 'RIDE';
    const needsRiderCheck = arrived && isRide && !riderConfirmed && !codeLocked;
    return (
      <div className="space-y-4" data-testid="trip-live">
        {/* The map. Back and SOS float on its top edge; its bottom edge,
            where Google's logo and terms sit, is left clear. */}
        {/* Smaller once she has arrived: the check, the code or End trip is the job now, not the road. */}
        <div
          className={`relative -mx-screen -mt-6 overflow-hidden shadow-lift transition-[height] duration-500 ${arrived || atDrop ? 'h-[28vh] min-h-[11rem]' : 'h-[46vh] min-h-[17.5rem]'}`}
          data-testid="trip-map"
        >
          {/* One map at a time: while navigation is open it has its own. */}
          {!navigating && <LiveMap markers={markers} route={route?.points} fill />}
          <div className="pointer-events-none absolute inset-x-3 top-3 z-10 flex items-start justify-between">
            <button
              type="button"
              onClick={goBack}
              aria-label={t('trip.back')}
              className="pointer-events-auto flex h-11 w-11 items-center justify-center rounded-full bg-surface text-text-primary shadow-float"
            >
              <ArrowLeft className="h-5 w-5" aria-hidden="true" />
            </button>
            <div className="pointer-events-auto">
              <PartnerSos bookingId={bookingId} position={myPosition} />
            </div>
          </div>
        </div>

        {loadError && (
          <p className="text-center text-xs text-text-secondary" data-testid="trip-reconnecting">{t('trip.reconnecting')}</p>
        )}

        {/* Her rider asking to go somewhere else. */}
        <DestinationChangePrompt booking={booking} onAnswered={refreshBooking} />

        {/* What to do now. */}
        <Card className="space-y-4" data-testid="trip-step">
          <div className="flex items-center justify-between gap-2">
            <StatusBadge tone="primary">{phase === 'PICKUP' ? t('trip.stepPickup') : t('trip.stepDrop')}</StatusBadge>
            {!arrived && !atDrop && (
              <span className="text-sm font-semibold text-accent-green-strong" data-testid="trip-eta">
                {minutes != null && kmLeft != null
                  ? t('trip.etaShort', { minutes, km: kmLeft })
                  : kmLeft != null
                    ? t('trip.kmShort', { km: kmLeft })
                    : ''}
              </span>
            )}
          </div>

          <div className="flex items-start gap-3">
            <span className="relative mt-1.5 flex h-3 w-3 shrink-0" aria-hidden="true">
              {/* Arrived: the dot pulses, so the change is seen at a glance from the handlebar. */}
              {(arrived || atDrop) && (
                <span className={`absolute inset-0 rounded-full motion-safe:animate-pulse-ring ${phase === 'PICKUP' ? 'bg-primary' : 'bg-accent-orange'}`} />
              )}
              <span className={`relative h-3 w-3 rounded-full ring-4 ${phase === 'PICKUP' ? 'bg-primary ring-primary/15' : 'bg-accent-orange ring-accent-orange/20'}`} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-heading text-card-title text-text-primary" data-testid="trip-step-title">{title}</p>
              <p className="mt-1 line-clamp-2 text-sm text-text-secondary">{destination.label}</p>
              {atDrop && (
                <p className="mt-2 text-sm font-medium text-accent-green-strong" data-testid="at-drop-hint">
                  {isRide ? t('trip.arrivedDropHint') : t('trip.arrivedDropHintParcel')}
                </p>
              )}
              {!arrived && !route && <p className="mt-1 text-xs text-text-secondary">{mapCaption()}</p>}
              {routeError && <p className="mt-1 text-xs text-text-secondary">{t('trip.routeError')}</p>}
            </div>
          </div>

          {/* Who she is collecting, with the one way to reach her - folded into the check while it is up. */}
          {!needsRiderCheck && (
          <div className="flex items-center gap-3 border-t border-border pt-3" data-testid="trip-rider">
            <Avatar url={rider?.photoUrl} name={rider?.firstName ?? undefined} size="md" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold text-text-primary">{rider?.firstName || t('trip.yourRider')}</p>
              <p className="truncate text-xs text-text-secondary">
                {rider ? (
                  <AggregateRatingText averageStars={rider.averageStars} totalRatings={rider.totalRatings} emptyLabel={t('trip.newRider')} />
                ) : t('trip.noNumbersShort')}
              </p>
            </div>
            <button
              type="button"
              aria-label={t('trip.messageRider')}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-background text-primary"
              onClick={() => navigate(`/chat/${bookingId}`)}
            >
              <MessageCircle className="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
          )}

          {/* The step's one action. */}
          {phase === 'PICKUP' && !arrived && (
            <div className="space-y-2">
              <Button fullWidth icon={<Navigation className="h-5 w-5" />} onClick={() => setNavigating(true)} data-testid="start-navigation">
                {t('trip.navigateToPickup')}
              </Button>
              <p className="text-center text-xs text-text-secondary" data-testid="pickup-not-yet">{t('trip.notAtPickupYet')}</p>
            </div>
          )}

          {/* Before the code: is this the woman in the app? A face and a
              name to check, and a way out that costs her nothing. */}
          {needsRiderCheck && (
            <div className="space-y-3 rounded-card border-2 border-primary/25 bg-primary-light/40 p-4 motion-safe:animate-fade-slide-in" data-testid="rider-check">
              <p className="font-heading text-card-title text-text-primary">{t('trip.riderCheck.title')}</p>
              <div className="flex items-center gap-3">
                <Avatar url={rider?.photoUrl} name={rider?.firstName ?? undefined} size="xl" />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-heading text-title text-text-primary">{rider?.firstName || t('trip.yourRider')}</p>
                  <p className="text-sm text-text-secondary">
                    {rider?.photoUrl ? t('trip.riderCheck.samePhoto') : t('trip.riderCheck.noPhoto')}
                  </p>
                </div>
              </div>
              <p className="text-sm text-text-primary"><SafetyText k="riderCheck.womenOnly" /></p>
              {booking.category === 'BIKE' && (
                <p className="flex items-center gap-2 text-sm font-medium text-text-primary">
                  <HelmetIcon className="h-4 w-4 shrink-0 text-accent-orange" />
                  {t('trip.riderCheck.helmet')}
                </p>
              )}
              <Button fullWidth onClick={confirmRider} data-testid="rider-check-yes">{t('trip.riderCheck.yes')}</Button>
              <Button
                fullWidth
                variant="secondary"
                icon={<UserX className="h-5 w-5" />}
                onClick={() => { setMismatchError(null); setAskingMismatch(true); }}
                data-testid="rider-check-no"
              >
                {t('trip.riderCheck.no')}
              </Button>
            </div>
          )}

          {arrived && !needsRiderCheck && (
            <div className="space-y-3" data-testid="pickup-code-step">
              <p className="flex items-start gap-2 text-sm text-text-primary">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                <span>{codeLocked ? <SafetyText k="pickupCode.locked" /> : <SafetyText k="pickupCode.askForCode" />}</span>
              </p>
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
                  <Button fullWidth disabled={busy || pickupCode.length !== PICKUP_CODE_LENGTH} onClick={handleConfirmPickup}>
                    {busy ? t('trip.checking') : t('trip.confirmAndStart')}
                  </Button>
                </>
              )}
              {codeLocked && codeError && <p className="text-sm text-danger">{codeError}</p>}
            </div>
          )}

          {phase === 'DROP' && (
            <div className="space-y-2">
              {!myPosition && <p className="text-center text-xs text-text-secondary">{t('trip.locationNeededToEnd')}</p>}
              {myPosition && !atDropOff && (
                <p className="text-center text-xs text-text-secondary" data-testid="away-from-drop">
                  {t('trip.awayFromDrop', { metres: Math.round(metresBetween(myPosition, booking.drop) / 10) * 10 })}
                </p>
              )}
              {atDropOff ? (
                <Button fullWidth variant="success" disabled={busy} onClick={() => handleComplete()} data-testid="end-trip">
                  {busy ? t('trip.ending') : t('trip.endTrip')}
                </Button>
              ) : (
                <>
                  <Button fullWidth icon={<Navigation className="h-5 w-5" />} onClick={() => setNavigating(true)} data-testid="start-navigation">
                    {t('trip.navigateToDrop')}
                  </Button>
                  <Button fullWidth variant="secondary" size="md" disabled={busy} onClick={() => handleComplete()} data-testid="end-trip">
                    {busy ? t('trip.ending') : t('trip.endTrip')}
                  </Button>
                </>
              )}
            </div>
          )}

          {error && <p className="text-sm text-danger">{error}</p>}
        </Card>

        {/* The whole trip, folded away until she wants it. */}
        <Card className="p-0">
          <details className="group" data-testid="trip-details">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4 [&::-webkit-details-marker]:hidden">
              <span className="font-heading text-card-title text-text-primary">
                {booking.type === 'RIDE' ? t('trip.ride') : t('trip.delivery')}
              </span>
              <span className="flex items-center gap-2">
                <span className="font-heading text-card-title text-text-primary">₹{booking.finalFare ?? booking.fareEstimate}</span>
                <ChevronDown className="h-5 w-5 text-text-secondary transition-transform group-open:rotate-180" aria-hidden="true" />
              </span>
            </summary>
            <div className="space-y-2 border-t border-border px-4 py-3 text-sm">
              <div className="flex items-start gap-2">
                <span className="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                <span className="text-text-primary"><span className="sr-only">{t('trip.pickup')}: </span>{booking.pickup.label}</span>
              </div>
              <div className="flex items-start gap-2">
                <span className="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full bg-accent-orange" aria-hidden="true" />
                <span className="text-text-primary"><span className="sr-only">{t('trip.drop')}: </span>{booking.drop.label}</span>
              </div>
              <p className="pt-1 text-xs text-text-secondary">{t('trip.noNumbers')}</p>
            </div>
          </details>
        </Card>

        {/* Quiet, but always there. */}
        <div className="flex items-center gap-3">
          {booking.status === 'ACCEPTED' && (
            <button
              type="button"
              disabled={busy}
              onClick={() => { setCancelError(null); setAskingWhy(true); }}
              className="h-11 flex-1 rounded-full border border-border bg-surface text-sm font-semibold text-danger"
              data-testid="cancel-trip"
            >
              {t('trip.cancelTrip')}
            </button>
          )}
          <ContactSupportButton phoneNumber={supportPhoneNumber} className="flex-1" />
        </div>
        {bookingId && <TripInsuranceActions bookingId={bookingId} status={booking.status} />}

        {/* Turn-by-turn, over everything. Dialogs below still open above it
            (a rider asking to change destination, the drop-off reason). */}
        {navigating && (
          <NavigationView
            phase={phase}
            bookingId={bookingId}
            destination={destination}
            prepared={prepared}
            nav={nav}
            position={myPosition}
            rerouting={rerouting}
            routeError={routeError}
            onExit={() => setNavigating(false)}
          />
        )}

        <CancelReasonDialog<DropOffReason>
          open={askingDropReason}
          title={ds('dropOff.title')}
          message={ds('dropOff.message')}
          options={DROP_OFF_REASONS}
          busy={busy}
          error={dropReasonError}
          keepLabel={ds('dropOff.keep')}
          confirmLabel={ds('dropOff.confirm')}
          busyLabel={ds('dropOff.ending')}
          confirmVariant="primary"
          onConfirm={(reason, note) => handleComplete(reason, note)}
          onCancel={() => setAskingDropReason(false)}
        />
        <CancelReasonDialog
          open={askingWhy}
          options={DRIVER_CANCELLATION_REASONS}
          busy={busy}
          error={cancelError}
          onConfirm={handleCancel}
          onCancel={() => setAskingWhy(false)}
        />
        <ConfirmDialog
          open={askingMismatch}
          title={t('trip.mismatch.title')}
          message={mismatchError ?? t('trip.mismatch.body')}
          confirmLabel={busy ? t('trip.mismatch.cancelling') : t('trip.mismatch.confirm')}
          cancelLabel={t('trip.mismatch.back')}
          destructive
          onConfirm={handleMismatch}
          onCancel={() => setAskingMismatch(false)}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={finished ? t('trip.detailsTitle') : t('trip.title')} onBack={goBack} />

      {/* Help within one tap for as long as the trip is live. */}
      {tripIsLive && (
        <div className="flex justify-end">
          <PartnerSos bookingId={bookingId} position={myPosition} />
        </div>
      )}

      <div className="space-y-1">
        <LiveMap markers={markers} route={route?.points} />
        <p className="text-xs text-text-secondary">{mapCaption()}</p>
      </div>

      {(error ?? loadError) && <p className="text-sm text-danger">{error ?? loadError}</p>}
      {!booking && !error && !loadError && <SkeletonCard lines={4} label={t('trip.loading')} />}

      {booking && (
        <>
          {/* Once the trip is over, getting paid is the job in front of her. */}
          {booking.status === 'COMPLETED' && bookingId && (
            <CollectPaymentCard
              bookingId={bookingId}
              onPaid={(payment, justNow) => {
                setPaid(true);
                if (justNow) setPaidFlash(payment);
              }}
            />
          )}

          {/* Her rider asking to go somewhere else - a dialog while it
              waits, then a card saying what was agreed. */}
          <DestinationChangePrompt booking={booking} onAnswered={refreshBooking} />

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
                  <p className="font-heading text-card-title text-text-primary">
                    {phase === 'PICKUP' ? t('trip.goToPickup') : t('trip.goToDrop')}
                  </p>
                  <p className="mt-1 text-sm text-text-secondary">{destination.label}</p>
                </div>
              </div>
              <OpenInMapsButton
                lat={destination.lat}
                lng={destination.lng}
                label={destination.label}
                variant={phase === 'PICKUP' ? 'primary' : 'secondary'}
              >
                {phase === 'PICKUP' ? t('trip.navigateToPickup') : t('trip.navigateToDrop')}
              </OpenInMapsButton>
            </Card>
          )}

          {/* Said, not left to guess: the code card below appears only at
              the pickup, and without this she had no idea why it was missing. */}
          {booking.status === 'ACCEPTED' && !pickupArrived && (
            <p className="text-center text-sm text-text-secondary" data-testid="pickup-not-yet">
              {t('trip.notAtPickupYet')}
            </p>
          )}

          {/* The gate between the two phases. */}
          {booking.status === 'ACCEPTED' && pickupArrived && (
            <Card className="space-y-3">
              <div className="flex items-start gap-3">
                <IconCircle tone="soft" icon={<CheckCircle2 />} />
                <div className="min-w-0 flex-1">
                  <p className="font-heading text-card-title text-text-primary">{t('trip.confirmPickup')}</p>
                  <p className="mt-1 text-sm text-text-secondary">
                    {codeLocked
                      ? <SafetyText k="pickupCode.locked" />
                      : <SafetyText k="pickupCode.askForCode" />}
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
                    {busy ? t('trip.checking') : t('trip.confirmAndStart')}
                  </Button>
                </>
              )}
              {codeLocked && codeError && <p className="text-sm text-danger">{codeError}</p>}
            </Card>
          )}

          <Card className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="font-heading text-card-title text-text-primary">{booking.type === 'RIDE' ? t('trip.ride') : t('trip.delivery')}</p>
              <StatusBadge
                tone={
                  booking.status === 'COMPLETED' ? (settled ? 'success' : 'warning')
                    : booking.status === 'CANCELLED' ? 'danger'
                      : 'primary'
                }
              >
                {booking.status === 'COMPLETED' && !settled ? t('trip.awaitingPayment') : bookingStatusLabel(booking.status)}
              </StatusBadge>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex items-start gap-2">
                <Navigation className="mt-1 h-4 w-4 shrink-0 text-primary" />
                <span className="text-text-primary">{booking.pickup.label}</span>
              </div>
              <div className="flex items-start gap-2">
                <Navigation className="mt-1 h-4 w-4 shrink-0 text-accent-orange" />
                <span className="text-text-primary">{booking.drop.label}</span>
              </div>
            </div>
            <div className="flex justify-between border-t border-border pt-3 text-sm">
              <span className="text-text-secondary">{booking.status === 'CANCELLED' ? t('trip.fareNotCharged') : t('trip.fare')}</span>
              <span className="font-heading text-card-title text-text-primary">₹{booking.finalFare ?? booking.fareEstimate}</span>
            </div>
            {/* When it happened - the first thing anybody looks for in a
                past trip, and what support will ask for. */}
            {finished && (
              <dl className="space-y-2 border-t border-border pt-3 text-sm" data-testid="trip-timeline">
                {[
                  ['trip.when.requested', booking.requestedAt],
                  ['trip.when.started', booking.startedAt],
                  ['trip.when.completed', booking.completedAt],
                  ['trip.when.cancelled', booking.cancelledAt],
                ]
                  .filter((row): row is [string, string] => Boolean(row[1]))
                  .map(([label, at]) => (
                    <div key={label} className="flex justify-between gap-3">
                      <dt className="text-text-secondary">{t(label)}</dt>
                      <dd className="text-text-primary">{formatWhen(at)}</dd>
                    </div>
                  ))}
              </dl>
            )}
          </Card>

          {/* Who she is collecting - first name, photo and rating, released
              by the server once she has accepted. This was a placeholder
              reading "Customer details unavailable (mock)". */}
          <Card className="flex items-center gap-3" data-testid="trip-rider">
            <Avatar url={rider?.photoUrl} name={rider?.firstName ?? undefined} size="md" />
            <div className="min-w-0 flex-1">
              <p className="font-heading text-card-title text-text-primary">{rider?.firstName || t('trip.yourRider')}</p>
              <p className="text-xs text-text-secondary">
                {rider ? (
                  <AggregateRatingText averageStars={rider.averageStars} totalRatings={rider.totalRatings} emptyLabel={t('trip.newRider')} />
                ) : null}
                {rider ? ' · ' : ''}
                {t('trip.noNumbers')}
              </p>
            </div>
            <button
              aria-label={t('trip.messageRider')}
              className="rounded-full p-3 text-primary hover:bg-background"
              onClick={() => navigate(`/chat/${bookingId}`)}
            >
              <MessageCircle className="h-5 w-5" />
            </button>
          </Card>

          <div className="space-y-3">
            {/* The trip is over. A checkmark drawing itself, once, in half a
                second - not confetti: she has finished a piece of work, and
                the same screen shows after a trip that went badly. */}
            {/* Only once the fare is in. A trip is not complete while it is
                unpaid, and drawing a success check over an unpaid fare is
                what made ending a trip look like the end of the matter. */}
            {booking.status === 'COMPLETED' && settled && justEnded && (
              <Card className="flex flex-col items-center gap-2 py-6 text-center">
                <SuccessCheck size={64} label={t('trip.completed')} />
                <p className="font-heading text-card-title text-text-primary">{t('trip.completed')}</p>
                <p className="text-sm text-text-secondary">
                  {t('trip.earningsInWallet')}
                </p>
              </Card>
            )}
            {booking.status === 'COMPLETED' && settled && justEnded && (
              <Button fullWidth variant="secondary" onClick={() => navigate('/home', { replace: true })}>
                {t('trip.done')}
              </Button>
            )}
            {/* Unpaid. She is not offered new work until the fare lands or
                the hold runs out - the server enforces that; this says so,
                and when. */}
            {booking.status === 'COMPLETED' && !settled && hold && (
              <p className="text-center text-sm text-text-secondary" data-testid="payment-hold">
                {hold.holdUntil
                  ? t('home.holdBodyUntil', { time: new Date(hold.holdUntil).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) })
                  : t('home.holdBody')}
              </p>
            )}
            {booking.status === 'COMPLETED' && !settled && hold === null && (
              <>
                <p className="text-center text-sm text-text-secondary" data-testid="payment-overdue">
                  {t('trip.overdue')}
                </p>
                <Button fullWidth variant="secondary" onClick={() => navigate('/home', { replace: true })}>
                  {t('trip.backHome')}
                </Button>
              </>
            )}
            {booking.status === 'IN_PROGRESS' && (
              <>
                {!myPosition && (
                  <p className="text-center text-sm text-text-secondary">{t('trip.locationNeededToEnd')}</p>
                )}
                {myPosition && !atDropOff && booking.drop && (
                  <p className="text-center text-sm text-text-secondary" data-testid="away-from-drop">
                    {t('trip.awayFromDrop', { metres: Math.round(metresBetween(myPosition, booking.drop) / 10) * 10 })}
                  </p>
                )}
                <Button fullWidth variant="success" disabled={busy} onClick={() => handleComplete()} data-testid="end-trip">
                {busy ? t('trip.ending') : t('trip.endTrip')}
                </Button>
              </>
            )}
            {(booking.status === 'MATCHED' || booking.status === 'ACCEPTED') && (
              <Button
                fullWidth
                variant="danger"
                disabled={busy}
                onClick={() => { setCancelError(null); setAskingWhy(true); }}
              >
                {t('trip.cancelTrip')}
              </Button>
            )}
            {bookingId && <TripInsuranceActions bookingId={bookingId} status={booking.status} />}
            <ContactSupportButton phoneNumber={supportPhoneNumber} />
          </div>
        </>
      )}

      <CancelReasonDialog<DropOffReason>
        open={askingDropReason}
        title={ds('dropOff.title')}
        message={ds('dropOff.message')}
        options={DROP_OFF_REASONS}
        busy={busy}
        error={dropReasonError}
        keepLabel={ds('dropOff.keep')}
        confirmLabel={ds('dropOff.confirm')}
        busyLabel={ds('dropOff.ending')}
        confirmVariant="primary"
        onConfirm={(reason, note) => handleComplete(reason, note)}
        onCancel={() => setAskingDropReason(false)}
      />

      <CancelReasonDialog
        open={askingWhy}
        options={DRIVER_CANCELLATION_REASONS}
        busy={busy}
        error={cancelError}
        onConfirm={handleCancel}
        onCancel={() => setAskingWhy(false)}
      />

      {/* The fare has landed, while she was watching. */}
      <PaymentSuccessFlash
        open={paidFlash != null}
        amount={paidFlash ? (paidFlash.driverPayout ?? paidFlash.amount) : null}
        title={t('trip.paidFlash.title')}
        message={t('trip.paidFlash.body', { name: rider?.firstName || t('trip.yourRider') })}
        onDone={() => setPaidFlash(null)}
      />
    </div>
  );
}

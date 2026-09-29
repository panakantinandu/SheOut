import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGoBack } from '../lib/useGoBack';
import { Button, ServiceArt, useRouteLine } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { UnpaidTripBanner } from '../components/UnpaidTripBanner';
import { ApiError, bookingApi, routesApi } from '../api/client';
import { BookingMapLayout } from '../components/BookingMapLayout';
import { FareEstimateCard } from '../components/FareEstimateCard';
import { LocationPicker } from '../components/LocationPicker';
import type { PickerMode } from '../components/LocationPicker';
import { QuoteSummary } from '../components/QuoteSummary';
import { ServiceAreaNotice } from '../components/ServiceAreaNotice';
import { currentPosition, describePoint, isInServiceArea } from '../lib/geocode';
import { apiErrorText } from '../lib/apiErrors';
import { findLiveTrip } from '../lib/liveTrip';
import { useFareQuote } from '../lib/useFareQuote';
import { useNearbyDrivers } from '../lib/useNearbyDrivers';
import { useSavedPlaces } from '../lib/useSavedPlaces';
import type { GeoAddress } from '../api/types';
import { useTranslation } from '@sheout/design-system';

/**
 * One-tap shortcuts, shown above the search box before anything is typed.
 * These used to be the ONLY way to set a drop, which is what made them a
 * placeholder; now that both fields have real address search and a map
 * picker behind them, four popular Hyderabad landmarks are just a
 * convenience for the most common destinations.
 */
const DROP_PRESETS: GeoAddress[] = [
  { label: 'Hitech City', lat: 17.4483, lng: 78.3915 },
  { label: 'Gachibowli', lat: 17.4401, lng: 78.3489 },
  { label: 'Secunderabad', lat: 17.4399, lng: 78.4983 },
  { label: 'Charminar', lat: 17.3616, lng: 78.4747 },
];

/**
 * The estimated fare is real and arrives before booking: POST
 * /api/v1/bookings/quote runs the same FareCalculator that booking
 * creation runs, so the number shown here is the number the booking is
 * created with, not an approximation. The call is debounced and creates
 * nothing server-side - see useFareQuote.
 */
export function RideBooking() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/home');
  const [pickup, setPickup] = useState<GeoAddress | null>(null);
  const [pickupError, setPickupError] = useState<string | null>(null);
  const [drop, setDrop] = useState<GeoAddress | null>(null);
  const [picking, setPicking] = useState<'pickup' | 'drop' | null>(null);
  const [pickerMode, setPickerMode] = useState<PickerMode>('search');
  const chipsRef = useRef<HTMLDivElement>(null);

  function openPicker(field: 'pickup' | 'drop', mode: PickerMode) {
    setPickerMode(mode);
    setPicking(field);
  }
  const [submitting, setSubmitting] = useState(false);
  const fare = useFareQuote({ type: 'RIDE', category: 'BIKE', pickup, drop });
  const [error, setError] = useState<string | null>(null);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [liveTripId, setLiveTripId] = useState<string | null>(null);
  /** Bumped when the server refuses a booking for an unpaid trip, so the banner re-checks. */
  const [unpaidCheck, setUnpaidCheck] = useState(0);

  // Try the device once on arrival as a convenience, and reverse-geocode it
  // so the row reads as a place rather than "Your Current Location". A
  // failure here is no longer terminal: pickup is a picker like the drop,
  // so a blocked or unavailable location just means choosing it by hand.
  useEffect(() => {
    let cancelled = false;
    currentPosition()
      .then(async ({ lat, lng }) => {
        const address = await describePoint(lat, lng, t('booking.currentLocation'));
        if (!cancelled) setPickup(address);
      })
      .catch((err: Error) => {
        if (!cancelled) setPickupError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleBookNow() {
    if (!pickup || !drop) return;
    setSubmitting(true);
    setError(null);
    try {
      const booking = await bookingApi.create({ type: 'RIDE', category: 'BIKE', pickup, drop });
      navigate(`/tracking/${booking.id}`);
    } catch (err) {
      setError(apiErrorText(err, 'booking.createError'));
      // Not verified yet is not an error she can fix by reading it: the
      // screen offers the door rather than only naming the wall.
      setNeedsVerification(err instanceof ApiError && err.body?.error === 'CUSTOMER_NOT_VERIFIED');
      // The banner above names the unpaid trip and links to it.
      if (err instanceof ApiError && err.body?.error === 'UNPAID_TRIP') setUnpaidCheck((n) => n + 1);
      // A trip already live: the way out is that trip, one tap away.
      setLiveTripId(err instanceof ApiError && err.body?.error === 'ACTIVE_BOOKING_EXISTS' ? await findLiveTrip('RIDE') : null);
    } finally {
      setSubmitting(false);
    }
  }

  // Both ends inside the boundary. Drives the fare card and the CTA: an
  // estimate for a trip that cannot be booked is misleading on its own,
  // regardless of what the eventual booking call would say.
  const servableTrip = isInServiceArea(pickup) && isInServiceArea(drop);

  // Partners who could take this right now, at approximate positions -
  // "yes, there really are people near you" before she commits to booking.
  // Her own Home and Work, offered first in the picker.
  const savedPlaces = useSavedPlaces();
  const nearby = useNearbyDrivers(pickup, 'BIKE', t('booking.nearbyPartner'));
  // The road from pickup to drop, drawn as soon as both are set.
  const route = useRouteLine(routesApi.preview, pickup, drop);
  const markers: MapMarker[] = [...nearby];
  if (pickup) markers.push({ key: 'pickup', lat: pickup.lat, lng: pickup.lng, label: t('booking.pickup'), kind: 'pickup' });
  if (drop) markers.push({ key: 'drop', lat: drop.lat, lng: drop.lng, label: t('booking.drop'), kind: 'drop' });

  return (
    <>
      <BookingMapLayout
        title={t('home.serviceRide')}
        onBack={goBack}
        pickup={pickup}
        drop={drop}
        pickupPlaceholder={pickupError ? t('booking.tapToChoosePickup') : t('booking.findingLocation')}
        onEdit={openPicker}
        active={picking}
        chipsRef={chipsRef}
        markers={markers}
        route={route}
        summary={
          <>
            <UnpaidTripBanner refreshKey={unpaidCheck} />
            <QuoteSummary state={servableTrip ? fare : { quote: null, loading: false, error: null }} kind="ride" serviceName={t('home.serviceRide')} pickup={pickup} drop={drop} />
            <ServiceAreaNotice pickup={pickup} drop={drop} />
            {pickupError && !pickup && <p className="text-xs text-text-secondary">{pickupError}</p>}
            {error && <p className="text-sm text-danger">{error}</p>}
            {liveTripId && (
              <Button variant="secondary" fullWidth onClick={() => navigate(`/tracking/${liveTripId}`)} data-testid="view-live-trip">
                {t('booking.viewLiveTrip')}
              </Button>
            )}
            {needsVerification && (
              <Button variant="secondary" fullWidth onClick={() => navigate('/verification')} data-testid="verify-now">
                {t('booking.verifyNow')}
              </Button>
            )}
            <Button fullWidth disabled={!pickup || !drop || !servableTrip || submitting} onClick={handleBookNow} data-testid="book-now">
              {submitting ? t('booking.booking') : t('booking.bookNow')}
            </Button>
          </>
        }
        more={
          <>
            {servableTrip && <FareEstimateCard state={fare} />}
            <p className="text-xs text-text-secondary">{t('booking.fixedPriceNote')}</p>
            {nearby.length > 0 && (
              <p className="flex items-center gap-2 text-xs font-medium text-primary" data-testid="nearby-count">
                <ServiceArt kind="ride" size="xs" />
                {t('booking.nearbyCount', { count: nearby.length })}
              </p>
            )}
          </>
        }
      />

      <LocationPicker
        open={picking !== null}
        title={picking === 'pickup' ? t('booking.setPickup') : t('booking.whereTo')}
        saved={savedPlaces}
        presets={DROP_PRESETS}
        allowCurrentLocation={picking === 'pickup'}
        initialMode={pickerMode}
        markerKind={picking === 'pickup' ? 'pickup' : 'drop'}
        startAt={picking === 'pickup' ? pickup : drop}
        below={chipsRef}
        onSelect={(address) => (picking === 'pickup' ? setPickup(address) : setDrop(address))}
        onClose={() => setPicking(null)}
      />
    </>
  );
}

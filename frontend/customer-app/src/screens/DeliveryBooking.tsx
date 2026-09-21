import { MapPin, Bike } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, IconCircle, TextField, LiveMap, TopHeader } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { UnpaidTripBanner } from '../components/UnpaidTripBanner';
import { ApiError, bookingApi } from '../api/client';
import { FareEstimateCard } from '../components/FareEstimateCard';
import { LocationPicker } from '../components/LocationPicker';
import type { PickerMode } from '../components/LocationPicker';
import { LocationRow } from '../components/LocationRow';
import { ServiceAreaNotice } from '../components/ServiceAreaNotice';
import { currentPosition, describePoint, isInServiceArea } from '../lib/geocode';
import { apiErrorText } from '../lib/apiErrors';
import { useFareQuote } from '../lib/useFareQuote';
import { useNearbyDrivers } from '../lib/useNearbyDrivers';
import { useSavedPlaces } from '../lib/useSavedPlaces';
import type { BookingCategory, GeoAddress } from '../api/types';
import { useTranslation } from '@sheout/design-system';

const DROP_PRESETS: GeoAddress[] = [
  { label: 'Hitech City', lat: 17.4483, lng: 78.3915 },
  { label: 'Gachibowli', lat: 17.4401, lng: 78.3489 },
  { label: 'Secunderabad', lat: 17.4399, lng: 78.4983 },
  { label: 'Charminar', lat: 17.3616, lng: 78.4747 },
];

// Title, details label and button text live in the translations under
// delivery.<key>.
const CONFIG: Record<'parcel' | 'lunchbox', { key: 'parcel' | 'lunchbox'; category: BookingCategory }> = {
  parcel: { key: 'parcel', category: 'PARCEL' },
  // Mockup's CTA here is "View Menu", implying a menu-browsing step before
  // checkout - the booking module has no line-items/menu concept at all
  // (RequestBookingCommand is just type/category/pickup/drop), so this
  // button actually creates the real booking immediately, same as every
  // other flow. Labeled "Place Order" instead so the copy doesn't promise
  // a menu screen that isn't coming next - flagged deviation, not a typo.
  lunchbox: { key: 'lunchbox', category: 'LUNCHBOX' },
};

const MEAL_TYPES = ['VEG', 'NON_VEG'] as const;
type MealType = (typeof MEAL_TYPES)[number];

const PLANS = ['DAILY', 'WEEKLY', 'MONTHLY'] as const;
type Plan = (typeof PLANS)[number];

/**
 * Shared by both /book/parcel and /book/lunchbox - same requestBooking
 * call, different category.
 * <p>
 * FLAGGED GAP: "Package Details" / "Meal Notes", the mockup's Veg/Non-Veg
 * toggle, and its "Choose Plan" (Daily/Weekly/Monthly) selector all have
 * nowhere to go on the backend - RequestBookingCommand only carries
 * type/category/pickup/drop. Rather than stuff any of this into pickup/
 * drop's `label` (which would silently corrupt what's meant to be a
 * location name), all of it below is local UI state only, matching the
 * mockup's layout but NOT sent to the server - noted inline and to you,
 * not hidden. In particular, "Weekly"/"Monthly" don't create recurring
 * bookings - there's no subscription concept anywhere in the booking
 * module, only single one-off requests - so selecting them here is purely
 * cosmetic today.
 */
export function DeliveryBooking() {
  const { t } = useTranslation();
  const { kind } = useParams<{ kind: 'parcel' | 'lunchbox' }>();
  const config = CONFIG[kind === 'lunchbox' ? 'lunchbox' : 'parcel'];
  const navigate = useNavigate();

  // Lunch Box is deferred for launch. Its Home tile is gone, but /book/:kind
  // is a URL anyone can still type, so the route is closed here too -
  // otherwise the one remaining path to creating a LUNCHBOX booking would
  // be a hand-typed address. Deliberately a redirect rather than deleting
  // the config below: the backend still accepts the category, so re-enabling
  // the feature is removing this guard and restoring the tile, nothing more.
  useEffect(() => {
    if (kind === 'lunchbox') navigate('/home', { replace: true });
  }, [kind, navigate]);

  const [pickup, setPickup] = useState<GeoAddress | null>(null);
  const [pickupError, setPickupError] = useState<string | null>(null);
  const [drop, setDrop] = useState<GeoAddress | null>(null);
  const [picking, setPicking] = useState<'pickup' | 'drop' | null>(null);
  const [pickerMode, setPickerMode] = useState<PickerMode>('search');
  const [details, setDetails] = useState('');
  const [mealType, setMealType] = useState<MealType>('VEG');
  const [plan, setPlan] = useState<Plan>('DAILY');
  const [submitting, setSubmitting] = useState(false);
  const fare = useFareQuote({ type: 'DELIVERY', category: config.category, pickup, drop });
  const [error, setError] = useState<string | null>(null);
  const [needsVerification, setNeedsVerification] = useState(false);
  /** Bumped when the server refuses a booking for an unpaid trip, so the banner re-checks. */
  const [unpaidCheck, setUnpaidCheck] = useState(0);
  const isLunchbox = kind === 'lunchbox';

  function openPicker(field: 'pickup' | 'drop', mode: PickerMode) {
    setPickerMode(mode);
    setPicking(field);
  }

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

  async function handleContinue() {
    if (!pickup || !drop) return;
    setSubmitting(true);
    setError(null);
    try {
      const booking = await bookingApi.create({ type: 'DELIVERY', category: config.category, pickup, drop });
      navigate(`/tracking/${booking.id}`);
    } catch (err) {
      setError(apiErrorText(err, 'booking.createError'));
      // Not verified yet is not an error she can fix by reading it: the
      // screen offers the door rather than only naming the wall.
      setNeedsVerification(err instanceof ApiError && err.body?.error === 'CUSTOMER_NOT_VERIFIED');
      // The banner above names the unpaid trip and links to it.
      if (err instanceof ApiError && err.body?.error === 'UNPAID_TRIP') setUnpaidCheck((n) => n + 1);
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
  const nearby = useNearbyDrivers(pickup, config.category, t('booking.nearbyPartner'));
  const markers: MapMarker[] = [...nearby];
  if (pickup) markers.push({ key: 'pickup', lat: pickup.lat, lng: pickup.lng, label: t('booking.pickup'), kind: 'pickup' });
  if (drop) markers.push({ key: 'drop', lat: drop.lat, lng: drop.lng, label: t('booking.drop'), kind: 'drop' });

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t(`delivery.${config.key}.title`)} onBack={() => navigate(-1)} />

      <UnpaidTripBanner refreshKey={unpaidCheck} />

      {/* Real map, same shared component the tracking screen uses. Shows the
          points actually chosen: pickup once geolocation resolves, drop once
          one is picked. It is a preview of the two endpoints, not a routed
          line - nothing here computes a road route. */}
      <div className="space-y-1">
        <LiveMap markers={markers} />
        <p className="text-xs text-text-secondary">
          {drop ? t('booking.mapBoth') : t('booking.mapPickDrop')}
        </p>
        {nearby.length > 0 && (
          <p className="flex items-center gap-1.5 text-xs font-medium text-primary" data-testid="nearby-count">
            <Bike className="h-3.5 w-3.5" aria-hidden="true" />
            {t('booking.nearbyCount', { count: nearby.length })}
          </p>
        )}
      </div>

      <Card className="divide-y divide-border p-0">
        <LocationRow
          icon={<IconCircle icon={<MapPin />} size="sm" />}
          label={t('booking.pickupLocation')}
          sublabel={pickup?.label ?? (pickupError ? t('booking.tapToChoosePickup') : t('booking.findingLocation'))}
          onSearch={() => openPicker('pickup', 'search')}
          onMap={() => openPicker('pickup', 'map')}
        />
        <LocationRow
          icon={<IconCircle color="orange" icon={<MapPin />} size="sm" />}
          label={t('booking.dropLocation')}
          sublabel={drop?.label ?? t('booking.selectDestination')}
          onSearch={() => openPicker('drop', 'search')}
          onMap={() => openPicker('drop', 'map')}
        />
      </Card>


      {/* Local-only, matching the mockup's layout - not sent to the backend, see file-level comment. */}
      {isLunchbox && (
        <>
          <div>
            <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('delivery.mealType')}</span>
            <div className="flex gap-2">
              <Button
                type="button"
                variant={mealType === 'VEG' ? 'success' : 'secondary'}
                size="md"
                fullWidth
                onClick={() => setMealType('VEG')}
              >
                {t('delivery.veg')}
              </Button>
              <Button
                type="button"
                variant={mealType === 'NON_VEG' ? 'primary' : 'secondary'}
                size="md"
                fullWidth
                onClick={() => setMealType('NON_VEG')}
              >
                {t('delivery.nonVeg')}
              </Button>
            </div>
          </div>

          <div>
            <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('delivery.choosePlan')}</span>
            <div className="flex gap-2">
              {PLANS.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPlan(p)}
                  className={
                    plan === p
                      ? 'flex-1 rounded-full bg-primary py-2 text-sm font-semibold text-text-inverse'
                      : 'flex-1 rounded-full border border-border py-2 text-sm font-medium text-text-secondary'
                  }
                >
                  {t(`delivery.plan.${p}`)}
                </button>
              ))}
            </div>
          </div>
        </>
      )}

      <TextField
        label={t(`delivery.${config.key}.details`)}
        placeholder={isLunchbox ? t('delivery.lunchbox.detailsPlaceholder') : t('delivery.parcel.detailsPlaceholder')}
        value={details}
        onChange={(e) => setDetails(e.target.value)}
      />

      {pickupError && !pickup && <p className="text-xs text-text-secondary">{pickupError}</p>}
      {error && <p className="text-sm text-danger">{error}</p>}
      {needsVerification && (
        <Button variant="secondary" fullWidth onClick={() => navigate('/verification')} data-testid="verify-now">
          {t('booking.verifyNow')}
        </Button>
      )}

      <ServiceAreaNotice pickup={pickup} drop={drop} />

      {servableTrip && <FareEstimateCard state={fare} />}

      <Button fullWidth disabled={!pickup || !drop || !servableTrip || submitting} onClick={handleContinue}>
        {submitting ? t('booking.booking') : t(`delivery.${config.key}.cta`)}
      </Button>

      <LocationPicker
        open={picking !== null}
        title={picking === 'pickup' ? t('booking.setPickup') : t('booking.whereTo')}
        saved={savedPlaces}
        presets={DROP_PRESETS}
        allowCurrentLocation={picking === 'pickup'}
        initialMode={pickerMode}
        markerKind={picking === 'pickup' ? 'pickup' : 'drop'}
        startAt={picking === 'pickup' ? pickup : drop}
        onSelect={(address) => (picking === 'pickup' ? setPickup(address) : setDrop(address))}
        onClose={() => setPicking(null)}
      />
    </div>
  );
}

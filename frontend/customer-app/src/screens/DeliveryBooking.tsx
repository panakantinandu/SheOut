import { MapPin } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, TextField, LiveMap, TopHeader } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, GeoAddress } from '../api/types';

const DROP_PRESETS: GeoAddress[] = [
  { label: 'Hitech City', lat: 17.4483, lng: 78.3915 },
  { label: 'Gachibowli', lat: 17.4401, lng: 78.3489 },
  { label: 'Secunderabad', lat: 17.4399, lng: 78.4983 },
  { label: 'Charminar', lat: 17.3616, lng: 78.4747 },
];

const CONFIG: Record<'parcel' | 'lunchbox', { title: string; category: BookingCategory; detailsLabel: string; cta: string }> = {
  parcel: { title: 'Parcel Delivery', category: 'PARCEL', detailsLabel: 'Package Details (optional)', cta: 'Continue' },
  // Mockup's CTA here is "View Menu", implying a menu-browsing step before
  // checkout - the booking module has no line-items/menu concept at all
  // (RequestBookingCommand is just type/category/pickup/drop), so this
  // button actually creates the real booking immediately, same as every
  // other flow. Labeled "Place Order" instead so the copy doesn't promise
  // a menu screen that isn't coming next - flagged deviation, not a typo.
  lunchbox: { title: 'Lunch Box Delivery', category: 'LUNCHBOX', detailsLabel: 'Meal Notes (optional)', cta: 'Place Order' },
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
  const [pickingDrop, setPickingDrop] = useState(false);
  const [details, setDetails] = useState('');
  const [mealType, setMealType] = useState<MealType>('VEG');
  const [plan, setPlan] = useState<Plan>('DAILY');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isLunchbox = kind === 'lunchbox';

  useEffect(() => {
    if (!navigator.geolocation) {
      setPickupError('Geolocation not supported by this browser');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => setPickup({ label: 'Your Current Location', lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => setPickupError('Could not get your location - allow location access and retry'),
      { timeout: 8000 }
    );
  }, []);

  async function handleContinue() {
    if (!pickup || !drop) return;
    setSubmitting(true);
    setError(null);
    try {
      const booking = await bookingApi.create({ type: 'DELIVERY', category: config.category, pickup, drop });
      navigate(`/tracking/${booking.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create booking - is the backend running?');
    } finally {
      setSubmitting(false);
    }
  }

  const markers: MapMarker[] = [];
  if (pickup) markers.push({ key: 'pickup', lat: pickup.lat, lng: pickup.lng, label: 'Pickup', kind: 'pickup' });
  if (drop) markers.push({ key: 'drop', lat: drop.lat, lng: drop.lng, label: 'Drop', kind: 'drop' });

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={config.title} onBack={() => navigate(-1)} />

      {/* Real map, same shared component the tracking screen uses. Shows the
          points actually chosen: pickup once geolocation resolves, drop once
          one is picked. It is a preview of the two endpoints, not a routed
          line - nothing here computes a road route. */}
      <div className="space-y-1">
        <LiveMap markers={markers} />
        <p className="text-xs text-text-secondary">
          {drop ? 'Pickup and drop shown below.' : 'Pick a destination to see it on the map.'}
        </p>
      </div>

      <Card className="divide-y divide-border p-0">
        <div className="p-4">
          <ListRow
            icon={<IconCircle icon={<MapPin />} size="sm" />}
            label="Pickup Location"
            sublabel={pickup?.label ?? pickupError ?? 'Fetching your location...'}
            chevron={false}
          />
        </div>
        <div className="p-4">
          <ListRow
            icon={<IconCircle color="orange" icon={<MapPin />} size="sm" />}
            label="Drop Location"
            sublabel={drop?.label ?? 'Select Destination'}
            onClick={() => setPickingDrop((v) => !v)}
          />
        </div>
      </Card>

      {pickingDrop && (
        <Card className="divide-y divide-border p-0">
          {DROP_PRESETS.map((preset) => (
            <div className="p-4" key={preset.label}>
              <ListRow
                label={preset.label}
                onClick={() => {
                  setDrop(preset);
                  setPickingDrop(false);
                }}
              />
            </div>
          ))}
        </Card>
      )}

      {/* Local-only, matching the mockup's layout - not sent to the backend, see file-level comment. */}
      {isLunchbox && (
        <>
          <div>
            <span className="mb-1.5 block text-sm font-medium text-text-primary">Meal Type</span>
            <div className="flex gap-2">
              <Button
                type="button"
                variant={mealType === 'VEG' ? 'success' : 'secondary'}
                size="md"
                fullWidth
                onClick={() => setMealType('VEG')}
              >
                Veg
              </Button>
              <Button
                type="button"
                variant={mealType === 'NON_VEG' ? 'primary' : 'secondary'}
                size="md"
                fullWidth
                onClick={() => setMealType('NON_VEG')}
              >
                Non-Veg
              </Button>
            </div>
          </div>

          <div>
            <span className="mb-1.5 block text-sm font-medium text-text-primary">Choose Plan</span>
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
                  {p.charAt(0) + p.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          </div>
        </>
      )}

      <TextField
        label={config.detailsLabel}
        placeholder={isLunchbox ? 'e.g. less spicy' : 'e.g. small box, handle with care'}
        value={details}
        onChange={(e) => setDetails(e.target.value)}
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button fullWidth disabled={!pickup || !drop || submitting} onClick={handleContinue}>
        {submitting ? 'Booking...' : config.cta}
      </Button>
    </div>
  );
}

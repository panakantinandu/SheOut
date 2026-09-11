import { MapPin } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, LiveMap, TopHeader } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import { FareEstimateCard } from '../components/FareEstimateCard';
import { LocationPicker } from '../components/LocationPicker';
import { currentPosition, describePoint } from '../lib/geocode';
import { useFareQuote } from '../lib/useFareQuote';
import type { GeoAddress } from '../api/types';

/**
 * FLAGGED: there's no places/geocoding API configured anywhere in this
 * project, so "Drop Location" is picked from a small fixed list of
 * Hyderabad landmarks with hardcoded coordinates rather than free-text
 * search - clearly a placeholder for real geocoding, not a finished
 * address picker.
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
  const navigate = useNavigate();
  const [pickup, setPickup] = useState<GeoAddress | null>(null);
  const [pickupError, setPickupError] = useState<string | null>(null);
  const [drop, setDrop] = useState<GeoAddress | null>(null);
  const [picking, setPicking] = useState<'pickup' | 'drop' | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const fare = useFareQuote({ type: 'RIDE', category: 'BIKE', pickup, drop });
  const [error, setError] = useState<string | null>(null);

  // Try the device once on arrival as a convenience, and reverse-geocode it
  // so the row reads as a place rather than "Your Current Location". A
  // failure here is no longer terminal: pickup is a picker like the drop,
  // so a blocked or unavailable location just means choosing it by hand.
  useEffect(() => {
    let cancelled = false;
    currentPosition()
      .then(async ({ lat, lng }) => {
        const address = await describePoint(lat, lng, 'Your Current Location');
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
      setError(err instanceof ApiError ? err.message : 'Could not create your booking. Please check your connection and try again.');
    } finally {
      setSubmitting(false);
    }
  }

  const markers: MapMarker[] = [];
  if (pickup) markers.push({ key: 'pickup', lat: pickup.lat, lng: pickup.lng, label: 'Pickup', kind: 'pickup' });
  if (drop) markers.push({ key: 'drop', lat: drop.lat, lng: drop.lng, label: 'Drop', kind: 'drop' });

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Bike Taxi" onBack={() => navigate(-1)} />

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

      <Card className="space-y-1 divide-y divide-border p-0">
        <div className="p-4">
          <ListRow
            icon={<IconCircle icon={<MapPin />} size="sm" />}
            label="Pickup Location"
            sublabel={pickup?.label ?? (pickupError ? 'Tap to choose your pickup point' : 'Finding your location...')}
            onClick={() => setPicking('pickup')}
          />
        </div>
        <div className="p-4">
          <ListRow
            icon={<IconCircle color="orange" icon={<MapPin />} size="sm" />}
            label="Drop Location"
            sublabel={drop?.label ?? 'Select Destination'}
            onClick={() => setPicking('drop')}
          />
        </div>
      </Card>


      {pickupError && !pickup && <p className="text-xs text-text-secondary">{pickupError}</p>}
      {error && <p className="text-sm text-danger">{error}</p>}

      <FareEstimateCard state={fare} />

      <Button fullWidth disabled={!pickup || !drop || submitting} onClick={handleBookNow}>
        {submitting ? 'Booking...' : 'Book Now'}
      </Button>

      <LocationPicker
        open={picking !== null}
        title={picking === 'pickup' ? 'Set pickup location' : 'Where to?'}
        presets={DROP_PRESETS}
        allowCurrentLocation={picking === 'pickup'}
        onSelect={(address) => (picking === 'pickup' ? setPickup(address) : setDrop(address))}
        onClose={() => setPicking(null)}
      />
    </div>
  );
}

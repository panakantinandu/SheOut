import { MapPin } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, LiveMap, TopHeader } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import { FareEstimateCard } from '../components/FareEstimateCard';
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
  const [pickingDrop, setPickingDrop] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const fare = useFareQuote({ type: 'RIDE', category: 'BIKE', pickup, drop });
  const [error, setError] = useState<string | null>(null);

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

  async function handleBookNow() {
    if (!pickup || !drop) return;
    setSubmitting(true);
    setError(null);
    try {
      const booking = await bookingApi.create({ type: 'RIDE', category: 'BIKE', pickup, drop });
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

      {error && <p className="text-sm text-danger">{error}</p>}

      <FareEstimateCard state={fare} />

      <Button fullWidth disabled={!pickup || !drop || submitting} onClick={handleBookNow}>
        {submitting ? 'Booking...' : 'Book Now'}
      </Button>
    </div>
  );
}

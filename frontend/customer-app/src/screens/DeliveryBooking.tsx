import { MapPin } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, TextField, TopHeader } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, GeoAddress } from '../api/types';
import { MapPlaceholder } from '../components/MapPlaceholder';

const DROP_PRESETS: GeoAddress[] = [
  { label: 'Hitech City', lat: 17.4483, lng: 78.3915 },
  { label: 'Gachibowli', lat: 17.4401, lng: 78.3489 },
  { label: 'Secunderabad', lat: 17.4399, lng: 78.4983 },
  { label: 'Charminar', lat: 17.3616, lng: 78.4747 },
];

const CONFIG: Record<'parcel' | 'lunchbox', { title: string; category: BookingCategory; detailsLabel: string }> = {
  parcel: { title: 'Parcel Delivery', category: 'PARCEL', detailsLabel: 'Package Details (optional)' },
  lunchbox: { title: 'Lunch Box Delivery', category: 'LUNCHBOX', detailsLabel: 'Meal Notes (optional)' },
};

/**
 * Shared by both /book/parcel and /book/lunchbox - same requestBooking
 * call, different category.
 * <p>
 * FLAGGED GAP: "Package Details" / "Meal Notes" / the mockup's Veg/Non-Veg
 * toggle have nowhere to go on the backend - BookingEntity/
 * RequestBookingCommand only carry type/category/pickup/drop, no
 * notes/details field. Rather than stuff this into pickup/drop's `label`
 * (which would silently corrupt what's meant to be a location name), the
 * details field below is local UI state only and is NOT sent to the
 * server - noted inline and to you, not hidden.
 */
export function DeliveryBooking() {
  const { kind } = useParams<{ kind: 'parcel' | 'lunchbox' }>();
  const config = CONFIG[kind === 'lunchbox' ? 'lunchbox' : 'parcel'];
  const navigate = useNavigate();

  const [pickup, setPickup] = useState<GeoAddress | null>(null);
  const [pickupError, setPickupError] = useState<string | null>(null);
  const [drop, setDrop] = useState<GeoAddress | null>(null);
  const [pickingDrop, setPickingDrop] = useState(false);
  const [details, setDetails] = useState('');
  const [submitting, setSubmitting] = useState(false);
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

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={config.title} onBack={() => navigate(-1)} />

      <MapPlaceholder label="Route preview" />

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

      {/* Local-only field - not sent to the backend, see file-level comment. */}
      <TextField
        label={config.detailsLabel}
        placeholder="e.g. small box, handle with care"
        value={details}
        onChange={(e) => setDetails(e.target.value)}
      />

      {error && <p className="text-sm text-danger">{error}</p>}

      <Button fullWidth disabled={!pickup || !drop || submitting} onClick={handleContinue}>
        {submitting ? 'Booking...' : 'Continue'}
      </Button>
    </div>
  );
}

import type { GeoAddress } from '../api/types';

/**
 * Address search and reverse lookup via Nominatim, OpenStreetMap's own
 * geocoder.
 * <p>
 * Chosen because it needs no API key and no billing account, and because
 * this app already draws OpenStreetMap tiles through Leaflet - so the
 * search results and the map agree about what places are called. The
 * trade-off is Nominatim's usage policy: it is a shared community service
 * with a hard limit of roughly one request per second and no uptime
 * guarantee. Every call site here is debounced for that reason.
 * <p>
 * FLAGGED FOR PRODUCTION: at real traffic this should move to a paid
 * geocoder (Google Places, Mapbox, Ola Maps) behind our own backend, so the
 * key is not in the client and the rate limit is ours. Nominatim is the
 * right choice for getting this working, not for scale.
 */
const NOMINATIM = 'https://nominatim.openstreetmap.org';

/** Biases results towards India, which is where this app operates. */
const COUNTRY_CODES = 'in';

interface NominatimPlace {
  display_name: string;
  lat: string;
  lon: string;
  name?: string;
  type?: string;
}

/** Trims Nominatim's very long display_name into something that fits a row. */
function shortLabel(place: NominatimPlace): string {
  const parts = place.display_name.split(',').map((p) => p.trim());
  const head = place.name && place.name.trim() ? place.name.trim() : parts[0];
  // Keep the locality/city that follows, which is what disambiguates two
  // places with the same name.
  const rest = parts.filter((p) => p !== head).slice(0, 2);
  return [head, ...rest].join(', ');
}

export async function searchPlaces(query: string, signal?: AbortSignal): Promise<GeoAddress[]> {
  const trimmed = query.trim();
  if (trimmed.length < 3) return [];

  const url =
    `${NOMINATIM}/search?format=jsonv2&limit=6&addressdetails=0` +
    `&countrycodes=${COUNTRY_CODES}&q=${encodeURIComponent(trimmed)}`;

  const res = await fetch(url, { signal, headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`Search failed (${res.status})`);
  const places: NominatimPlace[] = await res.json();
  return places.map((p) => ({ label: shortLabel(p), lat: Number(p.lat), lng: Number(p.lon) }));
}

/**
 * Turns coordinates into a human address, so a pickup taken from the device
 * reads as a place rather than "Your Current Location". Falls back to the
 * generic label rather than failing the whole pickup - knowing where you
 * are matters more than knowing what it is called.
 */
export async function describePoint(lat: number, lng: number, fallback: string): Promise<GeoAddress> {
  try {
    const url = `${NOMINATIM}/reverse?format=jsonv2&lat=${lat}&lon=${lng}&zoom=16&addressdetails=0`;
    const res = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error('reverse failed');
    const place: NominatimPlace = await res.json();
    return { label: place.display_name ? shortLabel(place) : fallback, lat, lng };
  } catch {
    return { label: fallback, lat, lng };
  }
}

/**
 * The device's position as a promise, with the browser's own error codes
 * turned into something a person can act on. The previous code collapsed
 * every failure into one message and offered no way forward.
 */
export function currentPosition(): Promise<{ lat: number; lng: number }> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('This browser cannot share your location. Search for your pickup point instead.'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      (err) => {
        if (err.code === err.PERMISSION_DENIED) {
          reject(new Error('Location permission is blocked. Allow it in your browser settings, or search for your pickup point.'));
        } else if (err.code === err.POSITION_UNAVAILABLE) {
          reject(new Error('Your location is unavailable right now. Try again, or search for your pickup point.'));
        } else {
          reject(new Error('Finding your location took too long. Try again, or search for your pickup point.'));
        }
      },
      { timeout: 10000, enableHighAccuracy: true, maximumAge: 30000 }
    );
  });
}

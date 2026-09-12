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

/**
 * Hyderabad, the only city SheOut launches in. Passed as a viewbox so
 * matching places here float to the top, without `bounded=1` - a customer
 * searching for somewhere outside the city should still find it rather than
 * get an empty list.
 */
const HYDERABAD_VIEWBOX = '78.24,17.20,78.62,17.60';

/**
 * Hyderabad's centre. Where the map picker opens before a pin exists.
 */
export const CITY_CENTRE = { lat: 17.4483, lng: 78.3915 };

/**
 * Nominatim's policy caps absolute traffic at one request per second, so
 * every call queues through here. Debouncing each input is not the same
 * guarantee: two fields, a search and a reverse lookup can each be within
 * their own debounce and still land in the same second.
 *
 * IDENTIFICATION: the policy asks for an identifying User-Agent. A browser
 * will not let us set one - `User-Agent` is a forbidden header name, and
 * fetch drops it silently rather than erroring, so code that "sets" it is
 * only pretending. What does identify us is the Referer, which the browser
 * attaches automatically and which carries this app's own domain. The
 * optional contact below is the other identifier Nominatim documents; it is
 * blank on purpose, because it is sent to a third party and nobody's
 * address should be put there without them choosing to.
 */
const NOMINATIM_CONTACT = '';

let lastRequestAt = 0;
const MIN_REQUEST_GAP_MS = 1100;

async function throttled<T>(run: () => Promise<T>): Promise<T> {
  const wait = Math.max(0, lastRequestAt + MIN_REQUEST_GAP_MS - Date.now());
  if (wait > 0) await new Promise((r) => setTimeout(r, wait));
  lastRequestAt = Date.now();
  return run();
}

function contactParam(): string {
  return NOMINATIM_CONTACT ? `&email=${encodeURIComponent(NOMINATIM_CONTACT)}` : '';
}

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
    `&countrycodes=${COUNTRY_CODES}&viewbox=${HYDERABAD_VIEWBOX}` +
    `&q=${encodeURIComponent(trimmed)}${contactParam()}`;

  const res = await throttled(() => fetch(url, { signal, headers: { Accept: 'application/json' } }));
  if (!res.ok) throw new Error(`Search failed (${res.status})`);
  const places: NominatimPlace[] = await res.json();
  return places.map((p) => ({ label: shortLabel(p), lat: Number(p.lat), lng: Number(p.lon) }));
}

/**
 * The same reverse lookup as describePoint, but it reports failure instead
 * of hiding it.
 * <p>
 * The difference matters because the two callers want opposite things. A
 * pickup taken from the device already knows where it is and only wants a
 * nicer name, so a failed lookup should not stop anything. A pin the
 * customer dropped is the opposite: the address text IS the thing they are
 * being asked to confirm, and quietly showing them raw coordinates dressed
 * up as a fallback label would have them confirm something they cannot
 * read.
 */
export async function reverseGeocode(lat: number, lng: number, signal?: AbortSignal): Promise<GeoAddress> {
  const url =
    `${NOMINATIM}/reverse?format=jsonv2&lat=${lat}&lon=${lng}&zoom=16&addressdetails=0${contactParam()}`;
  const res = await throttled(() => fetch(url, { signal, headers: { Accept: 'application/json' } }));
  if (!res.ok) throw new Error(`Lookup failed (${res.status})`);
  const place: NominatimPlace & { error?: string } = await res.json();
  if (place.error || !place.display_name) {
    throw new Error('No address found at that point');
  }
  return { label: shortLabel(place), lat, lng };
}

/**
 * Turns coordinates into a human address, so a pickup taken from the device
 * reads as a place rather than "Your Current Location". Falls back to the
 * generic label rather than failing the whole pickup - knowing where you
 * are matters more than knowing what it is called.
 */
export async function describePoint(lat: number, lng: number, fallback: string): Promise<GeoAddress> {
  try {
    return await reverseGeocode(lat, lng);
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

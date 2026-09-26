import type { GeoAddress } from '../api/types';
import { SERVICE_CENTRE, SERVICE_RADIUS_KM } from './geocode';

/**
 * Address search through Google Places (the "New" Places API), called from
 * the browser with the same key the maps already use.
 * <p>
 * WHY GOOGLE HERE. Nominatim knows Hyderabad's roads but rarely its
 * buildings: a search for an apartment block or a shop would land on the
 * street, or the neighbourhood, and a partner would arrive a few hundred
 * metres from the gate. Places knows premises and businesses by name, and
 * its Details call returns the coordinates of the place itself.
 * <p>
 * HOW IT IS BILLED, AND WHY THE SESSION TOKEN MATTERS. Every search in one
 * sitting carries the same session token, and so does the Details call for
 * the result she taps. Google then bills the whole sitting as one session
 * instead of per keystroke. A new token starts each time the picker opens.
 * <p>
 * The key is the browser key the maps load with. It is visible to anybody
 * who opens the app either way; what protects it is the website restriction
 * on it in Google Cloud, and the budget alert on the project.
 */
const API_KEY: string = import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? '';
const PLACES = 'https://places.googleapis.com/v1';

/** Places caps a bias circle at 50 km; the service area is biased to, not bounded by. */
const BIAS_RADIUS_M = Math.min(SERVICE_RADIUS_KM * 1000, 50_000);

export function placesAvailable(): boolean {
  return API_KEY.length > 0;
}

export function newSessionToken(): string {
  return crypto.randomUUID();
}

export interface PlaceSuggestion {
  placeId: string;
  /** "Inorbit Mall" */
  mainText: string;
  /** "Mindspace, Madhapur, Hyderabad" */
  secondaryText: string;
  /** Straight-line km from the service centre, when Google says - used to label out-of-area results before they are tapped. */
  kmFromCentre: number | null;
  /** A building, a business or a street address, rather than an area. */
  precise: boolean;
}

/**
 * Place types that name a specific spot someone can be collected from. A
 * locality or a sublocality is an area - "Madhapur" is two square miles -
 * and is sorted below these.
 */
const PRECISE_TYPES = new Set([
  'premise', 'subpremise', 'street_address', 'establishment', 'point_of_interest',
  'street_number', 'plus_code', 'intersection',
]);
const AREA_TYPES = new Set([
  'locality', 'sublocality', 'sublocality_level_1', 'sublocality_level_2', 'sublocality_level_3',
  'neighborhood', 'political', 'administrative_area_level_1', 'administrative_area_level_2',
  'administrative_area_level_3', 'postal_code', 'country', 'colloquial_area', 'route',
]);

/** Building/business first, then streets, then areas - within each, Google's own order. */
function precisionRank(types: string[]): number {
  if (types.some((type) => PRECISE_TYPES.has(type))) return 0;
  if (types.includes('route')) return 1;
  if (types.some((type) => AREA_TYPES.has(type))) return 2;
  return 1;
}

function isPrecise(types: string[]): boolean {
  return precisionRank(types) === 0;
}

async function places<T>(path: string, init: RequestInit, signal?: AbortSignal): Promise<T> {
  const res = await fetch(`${PLACES}${path}`, {
    ...init,
    signal,
    headers: { 'Content-Type': 'application/json', 'X-Goog-Api-Key': API_KEY, ...(init.headers ?? {}) },
  });
  if (!res.ok) throw new Error(`Places request failed (${res.status})`);
  return res.json() as Promise<T>;
}

interface AutocompleteResponse {
  suggestions?: {
    placePrediction?: {
      placeId: string;
      types?: string[];
      distanceMeters?: number;
      structuredFormat?: { mainText?: { text: string }; secondaryText?: { text: string } };
      text?: { text: string };
    };
  }[];
}

/** Suggestions as she types, most precise first. */
export async function suggestPlaces(query: string, sessionToken: string, signal?: AbortSignal): Promise<PlaceSuggestion[]> {
  const trimmed = query.trim();
  if (trimmed.length < 3) return [];
  const body = {
    input: trimmed,
    sessionToken,
    includedRegionCodes: ['in'],
    languageCode: 'en',
    locationBias: {
      circle: { center: { latitude: SERVICE_CENTRE.lat, longitude: SERVICE_CENTRE.lng }, radius: BIAS_RADIUS_M },
    },
    // Measured from the centre so a result outside the area can be labelled
    // before she taps it - the same rule the backend applies on booking.
    origin: { latitude: SERVICE_CENTRE.lat, longitude: SERVICE_CENTRE.lng },
  };
  const data = await places<AutocompleteResponse>('/places:autocomplete', { method: 'POST', body: JSON.stringify(body) }, signal);
  const suggestions = (data.suggestions ?? [])
    .map((s) => s.placePrediction)
    .filter((p): p is NonNullable<typeof p> => Boolean(p?.placeId))
    .map((p, order) => {
      const types = p.types ?? [];
      return {
        order,
        rank: precisionRank(types),
        suggestion: {
          placeId: p.placeId,
          mainText: p.structuredFormat?.mainText?.text ?? p.text?.text ?? '',
          secondaryText: p.structuredFormat?.secondaryText?.text ?? '',
          kmFromCentre: typeof p.distanceMeters === 'number' ? p.distanceMeters / 1000 : null,
          precise: isPrecise(types),
        } satisfies PlaceSuggestion,
      };
    });
  suggestions.sort((a, b) => a.rank - b.rank || a.order - b.order);
  return suggestions.map((s) => s.suggestion);
}

interface PlaceDetails {
  location?: { latitude: number; longitude: number };
  formattedAddress?: string;
  displayName?: { text: string };
  types?: string[];
}

export interface ResolvedPlace {
  address: GeoAddress;
  /** False for an area (a locality, a neighbourhood): the pin should be placed by hand. */
  precise: boolean;
}

/**
 * The exact coordinates of the place she tapped. Same session token as the
 * searches, which closes the session for billing.
 */
export async function resolvePlace(suggestion: PlaceSuggestion, sessionToken: string, signal?: AbortSignal): Promise<ResolvedPlace> {
  const data = await places<PlaceDetails>(
    `/places/${encodeURIComponent(suggestion.placeId)}?sessionToken=${encodeURIComponent(sessionToken)}&languageCode=en`,
    { method: 'GET', headers: { 'X-Goog-FieldMask': 'location,formattedAddress,displayName,types' } },
    signal
  );
  if (!data.location) throw new Error('Place has no location');
  const name = data.displayName?.text || suggestion.mainText;
  const label = [name, suggestion.secondaryText].filter(Boolean).join(', ');
  return {
    address: { label: label.slice(0, 250), lat: data.location.latitude, lng: data.location.longitude },
    precise: isPrecise(data.types ?? []),
  };
}

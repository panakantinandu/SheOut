import { Crosshair, MapPin, MapPinned, Search, X } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Card, IconCircle, LiveMap, TextField } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';
import {
  CITY_CENTRE,
  OUT_OF_AREA_MESSAGE,
  currentPosition,
  describePoint,
  isInServiceArea,
  reverseGeocode,
  searchPlaces,
} from '../lib/geocode';

/** Nominatim asks for roughly one request a second; this stays well inside that. */
const SEARCH_DEBOUNCE_MS = 500;

export type PickerMode = 'search' | 'map';

export interface LocationPickerProps {
  open: boolean;
  title: string;
  /** Shown as one-tap shortcuts above the search results. */
  presets?: GeoAddress[];
  /** Offers "Use my current location" - only meaningful for pickup. */
  allowCurrentLocation?: boolean;
  /** Which tab to open on. The map icon beside a field opens straight on 'map'. */
  initialMode?: PickerMode;
  /** Colours the dropped pin to match the field it is setting. */
  markerKind?: 'pickup' | 'drop';
  /** Where the map opens when no pin has been dropped yet. */
  startAt?: GeoAddress | null;
  onSelect: (address: GeoAddress) => void;
  onClose: () => void;
}

/**
 * A full-screen place picker: type an address, pick a saved shortcut, or
 * use the device's position.
 * <p>
 * This replaces a hardcoded list of four destinations that was the only way
 * to set a drop, and a pickup that could ONLY come from geolocation - so a
 * customer whose browser blocked location, or who simply wanted to be
 * collected somewhere else, could not book at all. Both ends are now
 * searchable and both have a way forward when the device says no.
 */
export function LocationPicker({
  open,
  title,
  presets = [],
  allowCurrentLocation = false,
  initialMode = 'search',
  markerKind = 'drop',
  startAt = null,
  onSelect,
  onClose,
}: LocationPickerProps) {
  const [mode, setMode] = useState<PickerMode>(initialMode);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<GeoAddress[]>([]);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);
  const inFlight = useRef<AbortController | null>(null);

  /** The pin the customer has dropped, and the address we resolved for it. */
  const [pin, setPin] = useState<{ lat: number; lng: number } | null>(null);
  const [pinAddress, setPinAddress] = useState<GeoAddress | null>(null);
  const [resolving, setResolving] = useState(false);
  const [pinError, setPinError] = useState<string | null>(null);
  const pinLookup = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setResults([]);
      setError(null);
      setPin(null);
      setPinAddress(null);
      setPinError(null);
      setResolving(false);
      return;
    }
    // Each opening starts on whichever tab the caller asked for, and on the
    // pin already set for this field if there is one, so re-opening shows
    // where the current choice actually is.
    setMode(initialMode);
    setPin(startAt ? { lat: startAt.lat, lng: startAt.lng } : null);
    setPinAddress(startAt);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /**
   * Resolves a dropped pin to an address. Kept separate from the search
   * request so a slow lookup for an abandoned pin cannot overwrite the one
   * the customer is actually looking at.
   */
  const resolvePin = useCallback(async (lat: number, lng: number) => {
    setPin({ lat, lng });
    setPinAddress(null);
    setPinError(null);
    setResolving(true);
    pinLookup.current?.abort();
    const controller = new AbortController();
    pinLookup.current = controller;
    try {
      setPinAddress(await reverseGeocode(lat, lng, controller.signal));
    } catch (err) {
      if ((err as Error).name === 'AbortError') return;
      setPinError(
        (err as Error).message === 'No address found at that point'
          ? 'No address found at that point. Move the pin somewhere closer to a road or landmark.'
          : 'Could not look up that point. Check your connection and try again.'
      );
    } finally {
      if (!controller.signal.aborted) setResolving(false);
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    const trimmed = query.trim();
    if (trimmed.length < 3) {
      setResults([]);
      setSearching(false);
      return;
    }
    setSearching(true);
    const timer = setTimeout(async () => {
      // Abandon the previous request so a slow earlier one cannot land after
      // a newer one and overwrite fresher results.
      inFlight.current?.abort();
      const controller = new AbortController();
      inFlight.current = controller;
      try {
        const found = await searchPlaces(trimmed, controller.signal);
        setResults(found);
        // Both messages name the way out rather than just the problem. A
        // lane or a gate often has no name the map knows, so "nothing found"
        // is a normal answer here, not a fault - and the pin always works.
        setError(
          found.length === 0
            ? 'No results. Try a different search, or drop a pin on the map.'
            : null
        );
      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          setError('Could not search right now. Check your connection, or drop a pin on the map instead.');
        }
      } finally {
        if (!controller.signal.aborted) setSearching(false);
      }
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query, open]);

  async function handleUseCurrent() {
    setLocating(true);
    setError(null);
    try {
      const { lat, lng } = await currentPosition();
      // The device can be anywhere. Someone opening the app from another
      // city should be told so here, not after filling in both ends.
      if (!isInServiceArea({ lat, lng })) {
        setError(`You appear to be outside our service area. ${OUT_OF_AREA_MESSAGE}`);
        return;
      }
      onSelect(await describePoint(lat, lng, 'Your Current Location'));
      onClose();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLocating(false);
    }
  }

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-background">
      <div className="flex items-center gap-3 px-screen pt-6">
        <h2 className="flex-1 font-heading text-lg font-semibold text-text-primary">{title}</h2>
        <button
          type="button"
          aria-label="Close"
          onClick={onClose}
          className="flex h-9 w-9 items-center justify-center rounded-full text-text-primary hover:bg-surface"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <div className="space-y-4 overflow-y-auto px-screen pb-8 pt-4">
        {/* Two ways to the same answer, in the app's own tab treatment
            rather than a map widget's. Typing an address suits somewhere
            that has a name; dropping a pin suits a gate, a lane or a
            building the map has never heard of. */}
        <div className="flex gap-2">
          {([
            { key: 'search', label: 'Search', icon: <Search className="h-4 w-4" /> },
            { key: 'map', label: 'Pick on map', icon: <MapPinned className="h-4 w-4" /> },
          ] as const).map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setMode(tab.key)}
              aria-pressed={mode === tab.key}
              className={
                mode === tab.key
                  ? 'flex flex-1 items-center justify-center gap-1.5 rounded-full bg-primary py-2 text-sm font-semibold text-text-inverse'
                  : 'flex flex-1 items-center justify-center gap-1.5 rounded-full border border-border py-2 text-sm font-medium text-text-secondary'
              }
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>

        {mode === 'map' ? (
          <MapPane
            pin={pin}
            address={pinAddress}
            resolving={resolving}
            error={pinError}
            markerKind={markerKind}
            onPick={resolvePin}
            onConfirm={(address) => {
              onSelect(address);
              onClose();
            }}
          />
        ) : (
        <>
        <TextField
          autoFocus
          icon={<Search className="h-4 w-4 text-text-secondary" />}
          placeholder="Search for an area, street or landmark"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />

        {allowCurrentLocation && (
          <Button variant="secondary" fullWidth disabled={locating} onClick={handleUseCurrent} icon={<Crosshair className="h-4 w-4" />}>
            {locating ? 'Finding you...' : 'Use my current location'}
          </Button>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
        {searching && <p className="text-sm text-text-secondary">Searching...</p>}

        {results.length > 0 && (
          // Out-of-area matches are shown, not hidden. Hiding a real address
          // would read as "we could not find it", which is a different and
          // untrue thing - the place exists, we just do not go there yet.
          // They are dimmed, labelled and not selectable.
          <Card className="divide-y divide-border p-0">
            {results.map((place) => {
              const servable = isInServiceArea(place);
              return (
                <button
                  key={`${place.lat},${place.lng}`}
                  type="button"
                  disabled={!servable}
                  aria-disabled={!servable}
                  onClick={() => {
                    if (!servable) return;
                    onSelect(place);
                    onClose();
                  }}
                  className={
                    servable
                      ? 'flex w-full items-center gap-3 p-4 text-left'
                      : 'flex w-full cursor-not-allowed items-center gap-3 p-4 text-left opacity-50'
                  }
                >
                  <IconCircle tone="soft" size="sm" color={servable ? undefined : 'red'} icon={<MapPin />} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-text-primary">{place.label}</span>
                    {!servable && (
                      <span className="block text-xs font-medium text-danger">Outside service area</span>
                    )}
                  </span>
                </button>
              );
            })}
          </Card>
        )}

        {presets.length > 0 && query.trim().length < 3 && (
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">Popular places</p>
            <Card className="divide-y divide-border p-0">
              {presets.map((preset) => (
                <button
                  key={preset.label}
                  type="button"
                  onClick={() => {
                    onSelect(preset);
                    onClose();
                  }}
                  className="flex w-full items-center gap-3 p-4 text-left"
                >
                  <IconCircle tone="soft" size="sm" color="orange" icon={<MapPin />} />
                  <span className="flex-1 text-sm text-text-primary">{preset.label}</span>
                </button>
              ))}
            </Card>
          </div>
        )}
        </>
        )}

        <p className="text-center text-xs text-text-secondary">Search results &copy; OpenStreetMap contributors</p>
      </div>
    </div>
  );
}

/**
 * The map half of the picker. A tap or a dragged pin reports a point, the
 * point is reverse-geocoded, and the resulting address is shown for the
 * customer to read before it is accepted. Nothing is chosen by coordinate
 * alone - a silent lat/lng is not something a person can check.
 */
function MapPane({
  pin,
  address,
  resolving,
  error,
  markerKind,
  onPick,
  onConfirm,
}: {
  pin: { lat: number; lng: number } | null;
  address: GeoAddress | null;
  resolving: boolean;
  error: string | null;
  markerKind: 'pickup' | 'drop';
  onPick: (lat: number, lng: number) => void;
  onConfirm: (address: GeoAddress) => void;
}) {
  const markers: MapMarker[] = pin
    ? [{ key: 'pin', lat: pin.lat, lng: pin.lng, label: 'Selected point', kind: markerKind }]
    : [];

  // Captured once, when the map opens: the pin already set for this field if
  // there is one, otherwise the city. Recomputing it on every render would
  // re-centre the map under the user mid-pan, and following the pin would
  // snap the view on every tap.
  const [initialCentre] = useState(() => (pin ? { lat: pin.lat, lng: pin.lng } : CITY_CENTRE));

  return (
    <div className="space-y-3">
      <LiveMap
        markers={markers}
        onPick={onPick}
        center={initialCentre}
        zoom={15}
        autoFit={false}
        className="h-72"
      />
      <p className="text-xs text-text-secondary">
        Tap anywhere on the map to drop a pin, or drag the pin to move it.
      </p>

      {!pin ? (
        <Card className="text-center">
          <p className="text-sm text-text-secondary">No pin yet. Tap the map to choose a point.</p>
        </Card>
      ) : resolving ? (
        <Card className="flex items-center gap-3">
          <IconCircle tone="soft" size="sm" icon={<MapPin />} />
          <p className="text-sm text-text-secondary">Looking up this address...</p>
        </Card>
      ) : error ? (
        <Card tone="danger" className="space-y-3">
          <p className="text-sm font-medium text-text-primary">Could not name this point</p>
          <p className="text-xs text-text-secondary">{error}</p>
          <Button variant="secondary" fullWidth onClick={() => onPick(pin.lat, pin.lng)}>
            Try again
          </Button>
        </Card>
      ) : address ? (
        // The address still gets shown for an out-of-area pin. Refusing to
        // name the place the customer just tapped would leave them guessing
        // whether the pin or the boundary was the problem.
        <Card tone={isInServiceArea(address) ? 'default' : 'danger'} className="space-y-3">
          <div className="flex items-start gap-3">
            <IconCircle
              tone="soft"
              size="sm"
              color={!isInServiceArea(address) ? 'red' : markerKind === 'pickup' ? undefined : 'orange'}
              icon={<MapPin />}
            />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-text-secondary">Pin dropped at</p>
              <p className="text-sm font-medium text-text-primary">{address.label}</p>
            </div>
          </div>
          {isInServiceArea(address) ? (
            <Button fullWidth onClick={() => onConfirm(address)}>
              Use this location
            </Button>
          ) : (
            <p className="text-xs font-medium text-danger">{OUT_OF_AREA_MESSAGE}</p>
          )}
        </Card>
      ) : null}
    </div>
  );
}

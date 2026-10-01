import { Crosshair, MapPin, MapPinned, Search, X, Home, Briefcase } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { Button, Card, IconCircle, LiveMap, TextField } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';
import {
  CITY_CENTRE,
  outOfAreaMessage,
  currentPosition,
  describePoint,
  isInServiceArea,
  searchPlaces,
} from '../lib/geocode';
import { useCenterPinAddress } from '../lib/useCenterPinAddress';
import { SERVICE_RADIUS_KM } from '../lib/geocode';
import {
  newSessionToken,
  placesAvailable,
  resolvePlace,
  suggestPlaces,
  type PlaceSuggestion,
} from '../lib/places';
import { useTranslation } from '@sheout/design-system';
import { useCloseOnBack } from '../lib/useGoBack';

/** Nominatim asks for roughly one request a second; this stays well inside that. */
const SEARCH_DEBOUNCE_MS = 500;

export type PickerMode = 'search' | 'map';

export interface LocationPickerProps {
  open: boolean;
  title: string;
  /** Shown as one-tap shortcuts above the search results. */
  presets?: GeoAddress[];
  /**
   * Her own saved places, above everything else: the two addresses she uses
   * most should be one tap, not a search she has already done twice.
   */
  saved?: SavedShortcut[];
  /** Offers "Use my current location" - only meaningful for pickup. */
  allowCurrentLocation?: boolean;
  /** Which tab to open on. The map icon beside a field opens straight on 'map'. */
  initialMode?: PickerMode;
  /** Colours the dropped pin to match the field it is setting. */
  markerKind?: 'pickup' | 'drop';
  /** Where the map opens when no pin has been dropped yet. */
  startAt?: GeoAddress | null;
  /**
   * Opens as a sheet under this element instead of over the whole screen,
   * so what is above it stays in view - on the booking map, the pickup and
   * drop chips, with the one being set lit up. A tap above the sheet closes
   * it without reaching the map or the chips; so do the close button and Back.
   */
  below?: RefObject<HTMLElement>;
  /**
   * Choosing on the map is handed to the caller's own map instead of a map
   * inside this picker. The booking screen passes it: its whole screen is
   * already a map, and a second one in the sheet beneath it was two maps for
   * one choice. Called with where to start the pin, and whether that is an
   * area she should narrow down to a gate.
   */
  onPickOnMap?: (start: GeoAddress | null, areaHint: boolean) => void;
  onSelect: (address: GeoAddress) => void;
  onClose: () => void;
}

/**
 * A place picker - a full screen, or a sheet under the booking chips: type
 * an address, pick a saved shortcut, or use the device's position.
 * <p>
 * This replaces a hardcoded list of four destinations that was the only way
 * to set a drop, and a pickup that could ONLY come from geolocation - so a
 * customer whose browser blocked location, or who simply wanted to be
 * collected somewhere else, could not book at all. Both ends are now
 * searchable and both have a way forward when the device says no.
 */
export interface SavedShortcut {
  kind: 'home' | 'work';
  name: string;
  address: GeoAddress;
}

export function LocationPicker({
  open,
  title,
  presets = [],
  allowCurrentLocation = false,
  initialMode = 'search',
  saved = [],
  markerKind = 'drop',
  startAt = null,
  below,
  onPickOnMap,
  onSelect,
  onClose,
}: LocationPickerProps) {
  const { t } = useTranslation();
  /** Where the sheet's top edge sits, when it opens under `below`. */
  const [sheetTop, setSheetTop] = useState<number | null>(null);
  const [mode, setMode] = useState<PickerMode>(initialMode);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<GeoAddress[]>([]);
  /** Google Places suggestions - the main search. `results` is Nominatim, kept as the fallback. */
  const [suggestions, setSuggestions] = useState<PlaceSuggestion[]>([]);
  const [usingGoogle, setUsingGoogle] = useState(false);
  const [resolvingPlace, setResolvingPlace] = useState(false);
  /** Set when the place she chose is an area, not a spot: she is asked to put the pin on the gate. */
  const [areaHint, setAreaHint] = useState(false);
  const sessionToken = useRef<string>(newSessionToken());
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);
  const inFlight = useRef<AbortController | null>(null);

  /** Where the map tab's pin starts: the current choice, or the area she searched for. */
  const [mapStart, setMapStart] = useState<GeoAddress | null>(null);
  // The phone's back button closes the picker, not the booking behind it.
  useCloseOnBack(open, onClose);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setResults([]);
      setSuggestions([]);
      setAreaHint(false);
      setError(null);
      setMapStart(null);
      return;
    }
    // Each opening starts on whichever tab the caller asked for, and on the
    // pin already set for this field if there is one, so re-opening shows
    // where the current choice actually is.
    setMode(initialMode);
    // One Places billing session per opening of the picker.
    sessionToken.current = newSessionToken();
    setMapStart(startAt);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const trimmed = query.trim();
    if (trimmed.length < 3) {
      setResults([]);
      setSuggestions([]);
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
        // Google first: it knows buildings and businesses by name, and
        // returns the place's own coordinates. Nominatim only if Google is
        // not configured here or does not answer - a search that works
        // roughly beats one that does not work at all.
        if (placesAvailable()) {
          try {
            const found = await suggestPlaces(trimmed, sessionToken.current, controller.signal);
            setSuggestions(found);
            setResults([]);
            setUsingGoogle(true);
            setError(found.length === 0 ? t('picker.noResults') : null);
            return;
          } catch (err) {
            if ((err as Error).name === 'AbortError') throw err;
          }
        }
        setUsingGoogle(false);
        setSuggestions([]);
        const found = await searchPlaces(trimmed, controller.signal);
        setResults(found);
        // Both messages name the way out rather than just the problem. A
        // lane or a gate often has no name the map knows, so "nothing found"
        // is a normal answer here, not a fault - and the pin always works.
        setError(
          found.length === 0
            ? t('picker.noResults')
            : null
        );
      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          setError(t('picker.searchError'));
        }
      } finally {
        if (!controller.signal.aborted) setSearching(false);
      }
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query, open]);

  /**
   * She tapped a Google suggestion: fetch where it actually is. A building or
   * business is used as it is. An area - a locality, a neighbourhood - is
   * not a place anybody can be collected from, so the map opens with the pin
   * on it and she is asked to move it to the exact spot.
   */
  async function handleSuggestion(suggestion: PlaceSuggestion) {
    setResolvingPlace(true);
    setError(null);
    try {
      const place = await resolvePlace(suggestion, sessionToken.current);
      // The session ends with a Details call; the next search is a new one.
      sessionToken.current = newSessionToken();
      if (!isInServiceArea(place.address)) {
        setError(outOfAreaMessage());
        return;
      }
      if (place.precise) {
        onSelect(place.address);
        onClose();
        return;
      }
      if (onPickOnMap) {
        onPickOnMap(place.address, true);
        return;
      }
      setMapStart(place.address);
      setAreaHint(true);
      setMode('map');
    } catch {
      setError(t('picker.searchError'));
    } finally {
      setResolvingPlace(false);
    }
  }

  async function handleUseCurrent() {
    setLocating(true);
    setError(null);
    try {
      const { lat, lng } = await currentPosition();
      // The device can be anywhere. Someone opening the app from another
      // city should be told so here, not after filling in both ends.
      if (!isInServiceArea({ lat, lng })) {
        setError(`${t('picker.youOutside')} ${outOfAreaMessage()}`);
        return;
      }
      onSelect(await describePoint(lat, lng, t('booking.currentLocation')));
      onClose();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLocating(false);
    }
  }

  useLayoutEffect(() => {
    if (!open || !below) return;
    const measure = () => {
      if (below.current) setSheetTop(below.current.getBoundingClientRect().bottom + 12);
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [open, below]);

  if (!open) return null;

  const asSheet = below != null && sheetTop != null;

  return (
    createPortal(
    <>
    {/* A tap above the sheet - on the chips, the map or the header's back arrow - closes it, as a tap outside any sheet does. It is still caught, so it never also moves the map or opens a chip. */}
    {asSheet && <div className="fixed inset-x-0 top-0 z-50" style={{ height: sheetTop }} aria-hidden="true" onClick={onClose} data-testid="picker-sheet-guard" />}
    <div
      className={
        asSheet
          ? 'fixed inset-x-0 bottom-0 z-50 mx-auto flex max-w-md animate-sheet-up flex-col rounded-t-card bg-background shadow-overlay'
          : 'fixed inset-0 z-50 flex flex-col bg-background'
      }
      style={asSheet ? { top: sheetTop } : undefined}
      data-testid="location-picker"
    >
      <div className={`flex items-center gap-3 px-screen ${asSheet ? 'pt-4' : 'pt-6'}`}>
        <h2 className="flex-1 font-heading text-section text-text-primary">{title}</h2>
        <button
          type="button"
          aria-label={t('common.close')}
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
            { key: 'search', label: t('picker.search'), icon: <Search className="h-4 w-4" /> },
            { key: 'map', label: t('picker.pickOnMap'), icon: <MapPinned className="h-4 w-4" /> },
          ] as const).map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => {
                // On the booking screen the map is the screen's own; leave the sheet for it.
                if (tab.key === 'map' && onPickOnMap) {
                  onPickOnMap(startAt, false);
                  return;
                }
                setMode(tab.key);
              }}
              aria-pressed={mode === tab.key}
              className={
                mode === tab.key
                  ? 'flex flex-1 items-center justify-center gap-2 rounded-full bg-primary py-2 text-sm font-semibold text-text-inverse'
                  : 'flex flex-1 items-center justify-center gap-2 rounded-full border border-border py-2 text-sm font-medium text-text-secondary'
              }
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>

        {mode === 'map' ? (
          <>
          {areaHint && (
            <p className="rounded-input bg-primary-light px-3 py-2 text-sm text-primary" data-testid="picker-area-hint">
              {t('picker.areaHint')}
            </p>
          )}
          <MapPane
            start={mapStart}
            markerKind={markerKind}
            onConfirm={(address) => {
              onSelect(address);
              onClose();
            }}
          />
          </>
        ) : (
        <>
        <TextField
          autoFocus
          icon={<Search className="h-4 w-4 text-text-secondary" />}
          placeholder={t('picker.searchPlaceholder')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />

        {allowCurrentLocation && (
          <Button variant="secondary" fullWidth disabled={locating} onClick={handleUseCurrent} icon={<Crosshair className="h-4 w-4" />}>
            {locating ? t('picker.findingYou') : t('picker.useCurrent')}
          </Button>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
        {searching && <p className="text-sm text-text-secondary">{t('picker.searching')}</p>}

        {resolvingPlace && <p className="text-sm text-text-secondary">{t('picker.searching')}</p>}

        {suggestions.length > 0 && (
          <Card className="divide-y divide-border p-0" data-testid="picker-suggestions">
            {suggestions.map((s) => {
              const servable = s.kmFromCentre == null || s.kmFromCentre <= SERVICE_RADIUS_KM;
              return (
                <button
                  key={s.placeId}
                  type="button"
                  disabled={!servable || resolvingPlace}
                  aria-disabled={!servable}
                  onClick={() => servable && handleSuggestion(s)}
                  className={servable ? 'flex w-full items-center gap-3 p-4 text-left' : 'flex w-full cursor-not-allowed items-center gap-3 p-4 text-left opacity-50'}
                >
                  <IconCircle tone="soft" size="sm" color={servable ? undefined : 'red'} icon={<MapPin />} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-text-primary">{s.mainText}</span>
                    {s.secondaryText && <span className="block truncate text-xs text-text-secondary">{s.secondaryText}</span>}
                    {!servable && <span className="block text-xs font-medium text-danger">{t('picker.outsideArea')}</span>}
                  </span>
                </button>
              );
            })}
          </Card>
        )}

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
                      <span className="block text-xs font-medium text-danger">{t('picker.outsideArea')}</span>
                    )}
                  </span>
                </button>
              );
            })}
          </Card>
        )}

        {saved.length > 0 && query.trim().length < 3 && (
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">{t('picker.saved')}</p>
            <Card className="divide-y divide-border p-0" data-testid="picker-saved">
              {saved.map((shortcut) => (
                <button
                  key={shortcut.kind}
                  type="button"
                  onClick={() => {
                    onSelect(shortcut.address);
                    onClose();
                  }}
                  className="flex w-full items-center gap-3 p-4 text-left"
                  data-testid={`picker-saved-${shortcut.kind}`}
                >
                  <IconCircle tone="soft" size="sm" icon={shortcut.kind === 'home' ? <Home /> : <Briefcase />} />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-medium text-text-primary">{shortcut.name}</span>
                    <span className="block truncate text-xs text-text-secondary">{shortcut.address.label}</span>
                  </span>
                </button>
              ))}
            </Card>
          </div>
        )}

        {presets.length > 0 && query.trim().length < 3 && (
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">{t('picker.popular')}</p>
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

        {/* Google requires this beside Places results shown without a Google map. */}
        <p className="text-center text-xs text-text-secondary">{usingGoogle && (mode === 'search' || areaHint) ? 'Powered by Google' : t('picker.osm')}</p>
      </div>
    </div>
    </>,
    document.body
    )
  );
}

/**
 * The map half of the full-screen picker, used where there is no map behind
 * it (saved places, changing the destination mid-trip). The same centre pin
 * as the booking screen: she moves the map, the pin stays in the middle, and
 * the address under it is shown for her to read before it is accepted.
 * Nothing is chosen by coordinate alone - a silent lat/lng is not something
 * a person can check.
 */
function MapPane({
  start,
  markerKind,
  onConfirm,
}: {
  start: GeoAddress | null;
  markerKind: 'pickup' | 'drop';
  onConfirm: (address: GeoAddress) => void;
}) {
  const { t } = useTranslation();
  const pin = useCenterPinAddress(start, true);
  // Captured once, when the map opens: recomputing it would re-centre the
  // map under her mid-pan.
  const [initialCentre] = useState(() => (start ? { lat: start.lat, lng: start.lng } : CITY_CENTRE));
  const busy = pin.moving || pin.resolving;
  const address = pin.address;

  return (
    <div className="space-y-3">
      <LiveMap
        markers={[]}
        center={initialCentre}
        zoom={16}
        autoFit={false}
        className="h-[48vh] min-h-[16rem]"
        centerPin={{
          kind: markerKind,
          label: markerKind === 'pickup' ? t('picker.pickupHere') : t('picker.dropHere'),
          onMoveStart: pin.onMoveStart,
          onIdle: pin.onIdle,
        }}
      />
      <p className="text-xs text-text-secondary">{t('picker.mapHint')}</p>

      <Card tone={address && !isInServiceArea(address) ? 'danger' : 'default'} className="space-y-3" data-testid="picker-pin-card">
        <div className="flex items-start gap-3">
          <IconCircle
            tone="soft"
            size="sm"
            color={address && !isInServiceArea(address) ? 'red' : markerKind === 'pickup' ? undefined : 'orange'}
            icon={<MapPin />}
          />
          <div className="min-w-0 flex-1">
            <p className="text-xs text-text-secondary">{t('picker.pinAt')}</p>
            <p className={`text-sm font-medium text-text-primary transition-opacity ${busy ? 'opacity-50' : ''}`} aria-live="polite">
              {pin.moving ? t('picker.movingPin') : pin.resolving ? t('picker.lookingUp') : address?.label ?? (pin.error ? t('picker.cannotName') : t('picker.lookingUp'))}
            </p>
          </div>
        </div>
        {pin.error && !busy && (
          <>
            <p className="text-xs text-text-secondary">{pin.error}</p>
            <Button variant="secondary" fullWidth onClick={pin.retry}>
              {t('common.tryAgain')}
            </Button>
          </>
        )}
        {address && !busy && !isInServiceArea(address) ? (
          // The address is still shown for an out-of-area pin: refusing to
          // name the place would leave her guessing whether the pin or the
          // boundary was the problem.
          <p className="text-xs font-medium text-danger">{outOfAreaMessage()}</p>
        ) : (
          <Button fullWidth disabled={!address || busy || Boolean(pin.error)} onClick={() => address && onConfirm(address)}>
            {t('picker.useThis')}
          </Button>
        )}
      </Card>
    </div>
  );
}

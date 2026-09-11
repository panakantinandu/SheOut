import { Crosshair, MapPin, Search, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button, Card, IconCircle, TextField } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';
import { currentPosition, describePoint, searchPlaces } from '../lib/geocode';

/** Nominatim asks for roughly one request a second; this stays well inside that. */
const SEARCH_DEBOUNCE_MS = 500;

export interface LocationPickerProps {
  open: boolean;
  title: string;
  /** Shown as one-tap shortcuts above the search results. */
  presets?: GeoAddress[];
  /** Offers "Use my current location" - only meaningful for pickup. */
  allowCurrentLocation?: boolean;
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
  onSelect,
  onClose,
}: LocationPickerProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<GeoAddress[]>([]);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);
  const inFlight = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setResults([]);
      setError(null);
    }
  }, [open]);

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
        setError(found.length === 0 ? 'No places found for that search.' : null);
      } catch (err) {
        if ((err as Error).name !== 'AbortError') setError('Could not search right now. Check your connection and try again.');
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
          <Card className="divide-y divide-border p-0">
            {results.map((place) => (
              <button
                key={`${place.lat},${place.lng}`}
                type="button"
                onClick={() => {
                  onSelect(place);
                  onClose();
                }}
                className="flex w-full items-center gap-3 p-4 text-left"
              >
                <IconCircle tone="soft" size="sm" icon={<MapPin />} />
                <span className="min-w-0 flex-1 truncate text-sm text-text-primary">{place.label}</span>
              </button>
            ))}
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

        <p className="text-center text-xs text-text-secondary">Search results &copy; OpenStreetMap contributors</p>
      </div>
    </div>
  );
}

import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useEffect, useRef } from 'react';
import { cn } from '../lib/cn';

export interface MapMarker {
  key: string;
  lat: number;
  lng: number;
  label: string;
  /** Visual role, not free-form styling - keeps the three marker meanings consistent across both apps. */
  kind: 'pickup' | 'drop' | 'driver';
}

export interface LiveMapProps {
  markers: MapMarker[];
  className?: string;
  /** Re-fit the viewport to the markers whenever they change. Off once the user has panned. */
  autoFit?: boolean;
  /**
   * Turns the map into a place picker: tapping anywhere reports that point,
   * and markers become draggable and report where they are dropped. Leave
   * it unset and the map stays a read-only view, which is what tracking and
   * the dashboard want.
   */
  onPick?: (lat: number, lng: number) => void;
  /** Where to open when there are no markers to fit. Defaults to all of India. */
  center?: { lat: number; lng: number };
  /** Zoom for `center`. Ignored once markers exist and autoFit is on. */
  zoom?: number;
}

const COLORS: Record<MapMarker['kind'], string> = {
  pickup: '#7c3aed',
  drop: '#0f766e',
  driver: '#dc2626',
};

/**
 * Leaflet + OpenStreetMap tiles. Deliberately NOT react-leaflet: this needs
 * one map instance, a handful of markers and an imperative "move this
 * marker" call, and going through a wrapper's component tree to do that
 * buys nothing here while pinning us to its React-version support matrix.
 * <p>
 * Marker positions come straight from props on every render - this
 * component never animates, interpolates, or predicts a position between
 * updates. A marker sits exactly where the caller last said it was, so a
 * driver marker only ever shows a position the driver really reported.
 * With a polling caller that means the marker jumps on each poll; that is
 * honest, and smoothing it would draw a driver where they have not been.
 */
export function LiveMap({ markers, className, autoFit = true, onPick, center, zoom }: LiveMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRefs = useRef<Map<string, L.Marker>>(new Map());
  // Held in a ref so the map's click handler can be bound once, at creation,
  // and still call the caller's current function. Binding it in an effect
  // that depends on onPick would add and remove a listener on every render
  // of the parent.
  const onPickRef = useRef(onPick);
  onPickRef.current = onPick;

  // Create the map once. Leaflet owns this DOM node outright, so it must not
  // be re-created on prop changes or React and Leaflet fight over the node.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = L.map(containerRef.current, { zoomControl: true, attributionControl: true });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);
    map.setView([20.5937, 78.9629], 5); // India, until real markers arrive
    map.on('click', (e: L.LeafletMouseEvent) => onPickRef.current?.(e.latlng.lat, e.latlng.lng));
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      markerRefs.current.clear();
    };
  }, []);

  // Opening view for a caller that is not auto-fitting - a picker that
  // starts zoomed out to the whole country makes the user pan before they
  // can drop a useful pin, and their first tap lands hundreds of kilometres
  // from where they meant.
  //
  // Deliberately keyed on the coordinates rather than the object, and with
  // no dependency on the markers: a caller re-rendering with the same place
  // must not yank the viewport back while the user is panning, and a caller
  // that already has a pin still needs the map pointed at it.
  const centerLat = center?.lat;
  const centerLng = center?.lng;
  useEffect(() => {
    const map = mapRef.current;
    if (!map || centerLat == null || centerLng == null) return;
    map.setView([centerLat, centerLng], zoom ?? 12);
  }, [centerLat, centerLng, zoom]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const seen = new Set<string>();
    for (const m of markers) {
      seen.add(m.key);
      const existing = markerRefs.current.get(m.key);
      if (existing) {
        existing.setLatLng([m.lat, m.lng]);
        existing.setTooltipContent(m.label);
        continue;
      }
      const marker = L.marker([m.lat, m.lng], { icon: buildIcon(m.kind), draggable: Boolean(onPick) })
        .addTo(map)
        .bindTooltip(m.label, { direction: 'top', offset: [0, -10] });
      // Dragging the pin is the same act as tapping a new spot, so it goes
      // through the same callback rather than a second one the caller would
      // have to keep in step.
      marker.on('dragend', () => {
        const { lat, lng } = marker.getLatLng();
        onPickRef.current?.(lat, lng);
      });
      markerRefs.current.set(m.key, marker);
    }
    for (const [key, marker] of markerRefs.current) {
      if (!seen.has(key)) {
        marker.remove();
        markerRefs.current.delete(key);
      }
    }

    if (autoFit && markers.length > 0) {
      const bounds = L.latLngBounds(markers.map((m) => [m.lat, m.lng] as [number, number]));
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 16 });
    }
    // Leaflet mis-sizes when its container was hidden or resized at mount.
    setTimeout(() => map.invalidateSize(), 0);
  }, [markers, autoFit, onPick]);

  return (
    <div
      ref={containerRef}
      className={cn('h-64 w-full rounded-card overflow-hidden z-0', onPick && '[&.leaflet-grab]:cursor-crosshair', className)}
    />
  );
}

function buildIcon(kind: MapMarker['kind']) {
  const color = COLORS[kind];
  return L.divIcon({
    className: '',
    html: `<span style="display:block;width:18px;height:18px;border-radius:9999px;background:${color};border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)"></span>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  });
}

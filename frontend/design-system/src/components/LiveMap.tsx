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
export function LiveMap({ markers, className, autoFit = true }: LiveMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRefs = useRef<Map<string, L.Marker>>(new Map());

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
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      markerRefs.current.clear();
    };
  }, []);

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
      const marker = L.marker([m.lat, m.lng], { icon: buildIcon(m.kind) })
        .addTo(map)
        .bindTooltip(m.label, { direction: 'top', offset: [0, -10] });
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
  }, [markers, autoFit]);

  return <div ref={containerRef} className={cn('h-64 w-full rounded-card overflow-hidden z-0', className)} />;
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

import { MapPin } from 'lucide-react';

/**
 * No Leaflet/Mapbox is configured anywhere in this project, so per the
 * brief this is a clearly-labeled placeholder, not a real map. Kept as its
 * own small component (not an inline styled div dropped into each screen)
 * since both the ride-booking and tracking screens need the same block -
 * reusing it beats duplicating markup, even though it isn't a shared
 * design-system primitive (it's app-specific mock content, not a piece of
 * the reviewed visual language).
 */
export function MapPlaceholder({ label }: { label: string }) {
  return (
    <div className="flex h-48 flex-col items-center justify-center gap-2 rounded-card border border-dashed border-border bg-background text-text-secondary">
      <MapPin className="h-6 w-6" />
      <p className="text-sm font-medium">{label}</p>
      <p className="text-xs">Map integration not built yet - placeholder only</p>
    </div>
  );
}

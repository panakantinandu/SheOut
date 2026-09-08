import { MapPin } from 'lucide-react';

/**
 * No Leaflet/Mapbox is configured anywhere in this project, so this is a
 * clearly-labeled placeholder, not a real map. App-specific mock content
 * (not a design-system primitive), same as the customer-app's identical
 * MapPlaceholder.
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

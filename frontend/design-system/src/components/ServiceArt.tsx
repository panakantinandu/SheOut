import bikeTaxiArt from '../assets/art/bike-taxi.webp';
import parcelArt from '../assets/art/parcel.webp';
import { cn } from '../lib/cn';

export { bikeTaxiArt, parcelArt };
export { default as womenArt } from '../assets/art/women.webp';
/** The Marketplace's picture, in the same 3D rounded style as the ride and parcel tiles. */
export { default as marketplaceArt } from '../assets/art/marketplace.webp';

export type ServiceArtKind = 'ride' | 'parcel';
export type ServiceArtSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

const art: Record<ServiceArtKind, string> = { ride: bikeTaxiArt, parcel: parcelArt };
const sizes: Record<ServiceArtSize, string> = {
  xs: 'h-5 w-5',
  sm: 'h-8 w-8',
  md: 'h-12 w-12',
  lg: 'h-16 w-16',
  xl: 'h-24 w-24',
};

/**
 * SheOut's own picture of a service - the purple scooter tile for a ride,
 * the parcel tile for a delivery - wherever the screen means "that
 * service", not a generic vehicle.
 * <p>
 * This replaced a generic bicycle glyph from the icon set that had crept
 * into a dozen places (the wallet, the fare card, a partner's trip card)
 * because the real artwork sat outside both apps, in the repository's
 * public/ folder, and only three screens reached it. It now lives here,
 * resized from 1254 px PNGs of ~1.5 MB to 480 px WebP of 25-40 KB, and every
 * screen takes it from this one place.
 * <p>
 * Decorative by default: the text beside it names the service. Pass a
 * label when the picture stands alone.
 */
export function ServiceArt({ kind, size = 'md', label, className }: {
  kind: ServiceArtKind;
  size?: ServiceArtSize;
  label?: string;
  className?: string;
}) {
  return (
    <img
      src={art[kind]}
      alt={label ?? ''}
      aria-hidden={label ? undefined : true}
      draggable={false}
      className={cn('shrink-0 select-none object-contain', sizes[size], className)}
    />
  );
}

/** The service a booking category belongs to, for picking its art. */
export function serviceArtFor(category: string | null | undefined): ServiceArtKind {
  return category === 'PARCEL' || category === 'LUNCHBOX' ? 'parcel' : 'ride';
}

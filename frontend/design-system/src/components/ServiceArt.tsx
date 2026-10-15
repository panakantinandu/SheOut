import bikeTaxiArt from '../assets/art/bike-taxi.svg';
import parcelArt from '../assets/art/parcel.svg';
import { cn } from '../lib/cn';

export { bikeTaxiArt, parcelArt };
export { default as womenArt } from '../assets/art/women.svg';
/** The Marketplace's picture, in the same rounded-tile style as the ride and parcel art. */
export { default as marketplaceArt } from '../assets/art/marketplace.svg';

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
 * and every screen takes it from this one place.
 * <p>
 * The pictures are SVGs drawn for SheOut (2026-10-14), replacing earlier
 * images whose source and licence were never recorded - see
 * docs/assets-licences.md.
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

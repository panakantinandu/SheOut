import { useState } from 'react';
import { User } from 'lucide-react';

export interface AvatarProps {
  /** Null, empty, or a URL that fails to load all fall back to the silhouette. */
  url?: string | null;
  /** Used as the image's alt text. */
  name?: string | null;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  className?: string;
}

const SIZES = {
  sm: 'h-8 w-8',
  md: 'h-12 w-12',
  lg: 'h-16 w-16',
  xl: 'h-24 w-24',
} as const;

const ICON_SIZES = {
  sm: 'h-4 w-4',
  md: 'h-6 w-6',
  lg: 'h-8 w-8',
  xl: 'h-12 w-12',
} as const;

/**
 * Somebody's photo, or a silhouette when there is not one.
 * <p>
 * The fallback is the reason this component exists. A missing photo is
 * normal - accounts created before photos were required have none - and the
 * alternative is the browser's broken-image glyph, which reads as a bug on
 * the one screen where a rider is deciding whether to get into a stranger's
 * vehicle. Nothing in this product should have a state with no way out, and
 * that includes a state that merely looks broken.
 * <p>
 * It also falls back when a URL is present but fails to load, which is not
 * hypothetical here: photos are served from presigned storage URLs that
 * expire, so a screen left open long enough will eventually hold a dead
 * link.
 */
export function Avatar({ url, name, size = 'md', className }: AvatarProps) {
  const [failed, setFailed] = useState(false);
  const showPhoto = Boolean(url) && !failed;

  return (
    <span
      className={[
        SIZES[size],
        'inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-background',
        className ?? '',
      ].join(' ')}
    >
      {showPhoto ? (
        <img
          src={url as string}
          alt={name ? `${name}'s photo` : 'Profile photo'}
          className="h-full w-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        <User className={[ICON_SIZES[size], 'text-text-secondary'].join(' ')} aria-hidden="true" />
      )}
    </span>
  );
}

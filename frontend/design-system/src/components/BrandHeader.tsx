import { SheOutWordmark } from './SheOutLogo';
import { cn } from '../lib/cn';
import { useTranslation } from 'react-i18next';

export type BrandHeaderSize = 'md' | 'lg';

export interface BrandHeaderProps {
  /**
   * 'lg' is the splash treatment: the wordmark sized to the viewport with a
   * soft orange glow behind it. 'md' is the entry-screen treatment: a fixed
   * width, no glow, tighter spacing.
   */
  size?: BrandHeaderSize;
  /** Optional line under the wordmark, e.g. "Empowering Women Partners". */
  footer?: string;
  /** The line between the orange rules. Defaults to the brand line. */
  tagline?: string;
  /**
   * Lets the mark breathe - a six-pixel rise and fall over four seconds, on
   * the two screens where it is the whole picture (splash and sign-in).
   * Off everywhere else: a logo moving above a form somebody is filling in
   * is a distraction, not a flourish.
   */
  float?: boolean;
  className?: string;
}

/**
 * The SheOut brand lockup: the wordmark from the logo sheet and the brand
 * line, "By women · For women".
 * <p>
 * This was duplicated as inline JSX in customer-app's Splash and Login,
 * and driver-app's Login had none of it - a small square Logo.jpeg and a
 * plain text heading instead, so the two apps did not read as the same
 * product. Extracting it here is what makes driver-app's Login a
 * three-line change rather than a re-implementation that would drift
 * again.
 * <p>
 * The illustration is imported from this package's own assets rather than
 * read from each app's public/ folder. driver-app never had a copy of the
 * file, which is the concrete reason its Login could not show the mark;
 * importing it here means neither app needs one.
 */
export function BrandHeader({ size = 'md', footer, tagline: taglineProp, float = false, className }: BrandHeaderProps) {
  const { t } = useTranslation('ds');
  const tagline = taglineProp ?? t('brand.tagline');
  const large = size === 'lg';

  return (
    <div className={cn('flex flex-col items-center', large ? 'gap-4' : 'mx-auto gap-2.5', className)}>
      <div className={cn('relative flex w-full items-center justify-center', float && 'motion-safe:animate-float')}>
        {large && (
          <span
            className="absolute h-56 w-56 max-h-[70vw] max-w-[70vw] rounded-full bg-accent-orange/20 blur-3xl"
            aria-hidden="true"
          />
        )}
        {/* The logo itself - the S as her profile, the O as the pin - not an illustration beside a typed word. */}
        <SheOutWordmark className={cn('relative', large ? 'w-[72%] max-w-[18rem]' : 'w-52')} />
      </div>

      {/* The logo's own line, spaced as the logo sheet sets it, between the orange rules. */}
      <div className="flex items-center gap-2">
        <span className="h-0.5 w-5 rounded-full bg-accent-orange" aria-hidden="true" />
        <p className={`font-heading font-bold uppercase tracking-[0.22em] text-primary-dark ${large ? 'text-xs' : 'text-[0.6875rem]'}`}>
          {tagline}
        </p>
        <span className="h-0.5 w-5 rounded-full bg-accent-orange" aria-hidden="true" />
      </div>

      {footer && <p className="text-xs font-medium text-text-secondary">{footer}</p>}
    </div>
  );
}

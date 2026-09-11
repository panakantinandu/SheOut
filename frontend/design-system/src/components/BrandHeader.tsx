import illustration from '../assets/sheout-illustration.png';
import { cn } from '../lib/cn';

export type BrandHeaderSize = 'md' | 'lg';

export interface BrandHeaderProps {
  /**
   * 'lg' is the splash treatment: the illustration sized to the viewport
   * with a soft orange glow behind it. 'md' is the entry-screen treatment:
   * a fixed 112px mark, no glow, tighter spacing.
   */
  size?: BrandHeaderSize;
  /** Optional line under the wordmark, e.g. "Empowering Women Partners". */
  footer?: string;
  className?: string;
}

/**
 * The SheOut brand lockup: illustration, SHEOUT wordmark with the orange
 * O, and the tagline.
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
export function BrandHeader({ size = 'md', footer, className }: BrandHeaderProps) {
  const large = size === 'lg';

  return (
    <div className={cn('flex flex-col items-center', large ? 'gap-3' : 'mx-auto gap-2', className)}>
      {large ? (
        <div className="relative flex w-full items-center justify-center">
          <span
            className="absolute h-56 w-56 max-h-[70vw] max-w-[70vw] rounded-full bg-accent-orange/20 blur-3xl"
            aria-hidden="true"
          />
          <img src={illustration} alt="SheOut" className="relative w-[58%] max-w-xs object-contain" />
        </div>
      ) : (
        <img src={illustration} alt="SheOut" className="h-28 w-28 object-contain" />
      )}

      {/* "SHE" purple, "OUT" orange - both sampled from the mockup. The
          previous version coloured only the O and left the rest near-black,
          which is not the wordmark. */}
      <p
        className={`font-heading font-extrabold tracking-tight text-primary ${
          large ? 'text-5xl' : 'text-4xl'
        }`}
      >
        SHE<span className="text-accent-brand-orange">OUT</span>
      </p>

      {/* Orange rules either side, as the mockup draws them - not em-dashes
          in italic grey, which is what this used to be. */}
      <div className="flex items-center gap-2">
        <span className="h-0.5 w-5 rounded-full bg-accent-orange" aria-hidden="true" />
        <p className={`font-heading font-bold text-primary-dark ${large ? 'text-sm' : 'text-xs'}`}>
          Your Delivery, Our Priority
        </p>
        <span className="h-0.5 w-5 rounded-full bg-accent-orange" aria-hidden="true" />
      </div>

      {footer && <p className="text-xs font-medium text-text-secondary">{footer}</p>}
    </div>
  );
}

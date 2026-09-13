import { Star } from 'lucide-react';

export interface StarRatingProps {
  /** 1-5, or null when nothing has been chosen yet. */
  value: number | null;
  /** Omit to render read-only. */
  onChange?: (stars: number) => void;
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  /** Announced to a screen reader, e.g. "Rate your partner". */
  label?: string;
}

const SIZES = {
  sm: 'h-4 w-4',
  md: 'h-6 w-6',
  lg: 'h-9 w-9',
} as const;

const STARS = [1, 2, 3, 4, 5];

/**
 * Five stars, tappable or not.
 * <p>
 * Real buttons rather than a styled radio group or a drag surface, because
 * this has to work on a phone with one thumb, in a hurry, at the end of a
 * trip. Every extra gesture between "I want to give four stars" and it being
 * recorded costs real participation, and an average built from only the
 * people angry enough to persevere is worse than no average.
 * <p>
 * Read-only mode takes no onChange and renders no buttons at all, so a
 * displayed score is never mistakable for something you can change.
 */
export function StarRating({
  value,
  onChange,
  size = 'md',
  disabled = false,
  label = 'Rating',
}: StarRatingProps) {
  const interactive = Boolean(onChange) && !disabled;
  const starClass = SIZES[size];

  if (!interactive) {
    return (
      <div className="flex items-center gap-0.5" role="img" aria-label={`${label}: ${value ?? 'not rated'} out of 5`}>
        {STARS.map((star) => (
          <Star
            key={star}
            className={[
              starClass,
              value !== null && star <= value ? 'fill-accent-orange text-accent-orange' : 'text-border',
            ].join(' ')}
          />
        ))}
      </div>
    );
  }

  return (
    <div className="flex items-center gap-1" role="radiogroup" aria-label={label}>
      {STARS.map((star) => {
        const filled = value !== null && star <= value;
        return (
          <button
            key={star}
            type="button"
            role="radio"
            aria-checked={value === star}
            aria-label={`${star} star${star === 1 ? '' : 's'}`}
            disabled={disabled}
            className="rounded-full p-1 transition-transform hover:scale-110 disabled:opacity-50"
            onClick={() => onChange?.(star)}
          >
            <Star
              className={[
                starClass,
                filled ? 'fill-accent-orange text-accent-orange' : 'text-border',
              ].join(' ')}
            />
          </button>
        );
      })}
    </div>
  );
}

export interface AggregateRatingProps {
  averageStars: number | null | undefined;
  totalRatings: number | null | undefined;
  /** Shown when nobody has rated yet. */
  emptyLabel?: string;
  className?: string;
}

/**
 * Somebody's score, in one line.
 * <p>
 * "Not rated yet" rather than a number when nobody has rated, and that is
 * not a nicety. Rendering 0.0, or an empty set of stars, for a partner on
 * her first night puts her below every person who has ever been rated badly
 * on the strength of no evidence at all - and it is the rider's first
 * impression of her.
 */
export function AggregateRatingText({
  averageStars,
  totalRatings,
  emptyLabel = 'Not rated yet',
  className,
}: AggregateRatingProps) {
  if (averageStars == null || !totalRatings) {
    return <span className={className}>{emptyLabel}</span>;
  }
  return (
    <span className={className}>
      ★ {averageStars.toFixed(1)} ({totalRatings})
    </span>
  );
}

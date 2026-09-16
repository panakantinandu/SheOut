import { useEffect, useState } from 'react';
import { Button } from './Button';
import { StarRating } from './StarRating';

/** One tappable reason, as the server's catalogue describes it. */
export interface RatingTagOption {
  code: string;
  label: string;
}

/** What to offer for a good rating, and what to offer for a poor one. */
export interface RatingTagCatalogue {
  positive: RatingTagOption[];
  negative: RatingTagOption[];
}

/** Four stars and up is a good trip. The same line the server draws - see RatingTag. */
const POSITIVE_FROM_STARS = 4;

export interface RatingDialogProps {
  open: boolean;
  title?: string;
  message?: string;
  /** Who is being rated, e.g. "your partner" - used in the placeholder. */
  counterpartLabel?: string;
  busy?: boolean;
  error?: string | null;
  /**
   * The quick reasons to offer, fetched from the server so both apps and the
   * console read the same words. Omit, or leave empty, and no tags are shown
   * - rating must never depend on a second request having succeeded.
   */
  tagOptions?: RatingTagCatalogue | null;
  onSubmit: (stars: number, comment?: string, tags?: string[]) => void;
  /** Closes without rating. Rating is optional and must stay that way. */
  onSkip: () => void;
}

const COMMENT_MAX = 500;

/**
 * Asks how the trip went, once it is over.
 * <p>
 * Built on the same bones as ConfirmDialog and CancelReasonDialog - backdrop
 * click and Escape both get you out, body scroll locked - so it feels like
 * the same app rather than a bolted-on survey.
 * <p>
 * Skipping is a real, equal-sized button and not a small grey link in a
 * corner. Rating is optional, and a dialog that makes the way out hard to
 * find is coercion dressed as a prompt: people tap a star at random to make
 * it go away, and the averages quietly fill up with noise. The comment box
 * only appears once stars are chosen, so the common case - four stars, done
 * - stays two taps.
 * <p>
 * No preselected default. A dialog that opens on five stars collects five
 * stars from everyone who taps submit without reading it, which is exactly
 * the population whose opinion the average most needs to be free of.
 */
export function RatingDialog({
  open,
  title = 'How was your trip?',
  message = 'Your rating is private. It is never shown to the other person, and it is not attached to your name.',
  counterpartLabel = 'them',
  busy = false,
  error = null,
  tagOptions = null,
  onSubmit,
  onSkip,
}: RatingDialogProps) {
  const [stars, setStars] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const [tags, setTags] = useState<string[]>([]);

  // Which half of the catalogue fits the stars just chosen. Null until she
  // has chosen any, because there is nothing to ask about yet.
  const band = stars === null ? null : stars >= POSITIVE_FROM_STARS ? 'positive' : 'negative';
  const offered = band && tagOptions ? tagOptions[band] : [];

  // Changing four stars to two has to drop what was tapped under the old
  // half: the server refuses a complaint sent with five stars, and silently
  // keeping them would turn a rating she meant to soften into a rejected
  // submission she cannot see the cause of.
  useEffect(() => {
    setTags([]);
  }, [band]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onSkip();
    };
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, busy, onSkip]);

  useEffect(() => {
    if (open) {
      setStars(null);
      setComment('');
      setTags([]);
    }
  }, [open]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-text-primary/40 px-4 py-6 sm:items-center"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={() => { if (!busy) onSkip(); }}
    >
      <div
        className="max-h-full w-full max-w-sm overflow-y-auto rounded-card bg-surface p-5 shadow-card"
        onClick={(e) => e.stopPropagation()}
      >
        <p className="font-heading text-lg font-semibold text-text-primary">{title}</p>
        <p className="mt-2 text-sm text-text-secondary">{message}</p>

        <div className="mt-5 flex justify-center">
          <StarRating value={stars} onChange={setStars} size="lg" disabled={busy} label={title} />
        </div>

        {/* One tap each, and none of them required. Almost nobody writes a
            comment, so without these a middling rating says nothing anybody
            can act on - and a question that costs a tap gets answered where
            a textarea does not. */}
        {offered.length > 0 && (
          <div className="mt-4 flex flex-wrap justify-center gap-2">
            {offered.map((option) => {
              const chosen = tags.includes(option.code);
              return (
                <button
                  key={option.code}
                  type="button"
                  disabled={busy}
                  aria-pressed={chosen}
                  onClick={() =>
                    setTags((current) =>
                      current.includes(option.code)
                        ? current.filter((code) => code !== option.code)
                        : [...current, option.code]
                    )
                  }
                  className={
                    'rounded-full border px-3 py-1.5 text-sm transition-colors ' +
                    (chosen
                      ? 'border-primary bg-primary text-white'
                      : 'border-border bg-surface text-text-primary')
                  }
                >
                  {option.label}
                </button>
              );
            })}
          </div>
        )}

        {stars !== null && (
          <label className="mt-4 block">
            <span className="mb-1.5 block text-sm font-medium text-text-primary">
              Anything you want to add? (optional)
            </span>
            <textarea
              className="min-h-[72px] w-full rounded-input border border-border bg-surface p-3 text-sm text-text-primary outline-none transition-colors focus:border-primary"
              maxLength={COMMENT_MAX}
              value={comment}
              disabled={busy}
              onChange={(e) => setComment(e.target.value)}
              placeholder={`What was it like travelling with ${counterpartLabel}?`}
            />
          </label>
        )}

        {error && <p className="mt-3 text-sm text-danger">{error}</p>}

        <div className="mt-5 flex gap-3">
          <Button variant="secondary" fullWidth disabled={busy} onClick={onSkip}>
            Not now
          </Button>
          <Button
            fullWidth
            disabled={stars === null || busy}
            onClick={() => stars !== null && onSubmit(stars, comment.trim() || undefined, tags)}
          >
            {busy ? 'Sending...' : 'Submit'}
          </Button>
        </div>
      </div>
    </div>
  );
}

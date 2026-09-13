import { useEffect, useState } from 'react';
import { Button } from './Button';
import { StarRating } from './StarRating';

export interface RatingDialogProps {
  open: boolean;
  title?: string;
  message?: string;
  /** Who is being rated, e.g. "your partner" - used in the placeholder. */
  counterpartLabel?: string;
  busy?: boolean;
  error?: string | null;
  onSubmit: (stars: number, comment?: string) => void;
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
  onSubmit,
  onSkip,
}: RatingDialogProps) {
  const [stars, setStars] = useState<number | null>(null);
  const [comment, setComment] = useState('');

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
            onClick={() => stars !== null && onSubmit(stars, comment.trim() || undefined)}
          >
            {busy ? 'Sending...' : 'Submit'}
          </Button>
        </div>
      </div>
    </div>
  );
}

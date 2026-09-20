import { useEffect, useState } from 'react';
import { Avatar } from './Avatar';
import { Button } from './Button';
import { Overlay } from './Overlay';
import { StarRating } from './StarRating';
import { useTranslation } from 'react-i18next';

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
  /** Her name, when this screen knows it. The sheet then asks about a person, not "them". */
  counterpartName?: string | null;
  counterpartPhotoUrl?: string | null;
  /** The trip in one line, e.g. "Kavuri Hills → Kondapur". Shown so she knows which one this is about. */
  tripSummary?: string | null;
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
 * A SHEET AT THE BOTTOM OF THE SCREEN, not a box in the middle of the page.
 * It used to be a centred card inside the page's own stacking context, which
 * put it halfway down a scrolled tracking screen with the dim layer stopping
 * short of the header - see Overlay, which is where that is now fixed. It
 * also opens where her thumb already is, at the end of a trip, on a phone.
 * <p>
 * IT SAYS WHO AND WHICH TRIP. Five grey stars under "How was your trip?" is
 * a survey; her partner's face and "Kavuri Hills to Kondapur" is a question
 * about something that just happened to her. The score means nothing unless
 * she is sure which trip she is scoring - and after a day of errands she is
 * not.
 * <p>
 * EACH STAR SAYS WHAT IT MEANS. "Good" and "Not good" under the stars, as
 * she taps them, so three stars means the same thing to her as it does to
 * the person reading the average. Nothing is preselected: a dialog that
 * opens on five stars collects five stars from everyone who taps submit
 * without reading it, which is exactly the population the average most
 * needs to be free of.
 * <p>
 * Skipping stays a real, reachable button. Rating is optional, and a sheet
 * that hides the way out is coercion dressed as a prompt: people tap a star
 * at random to make it go away and the averages fill up with noise.
 */
export function RatingDialog({
  open,
  title: titleProp,
  message: messageProp,
  counterpartLabel,
  counterpartName = null,
  counterpartPhotoUrl = null,
  tripSummary = null,
  busy = false,
  error = null,
  tagOptions = null,
  onSubmit,
  onSkip,
}: RatingDialogProps) {
  const { t } = useTranslation('ds');
  const who = counterpartLabel ?? t('rating.them');
  const title = titleProp ?? t('rating.title');
  const message = messageProp ?? t('rating.message');
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
    if (open) {
      setStars(null);
      setComment('');
      setTags([]);
    }
  }, [open]);

  return (
    <Overlay open={open} label={title} align="sheet" onDismiss={busy ? undefined : onSkip}>
      <div className="max-h-[92vh] overflow-y-auto rounded-t-[28px] bg-surface px-5 pb-6 pt-3 shadow-card motion-safe:animate-sheet-up">
        {/* The grabber: what every sheet on a phone has, so this reads as
            something she can push back down rather than something stuck. */}
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-border" aria-hidden="true" />

        <div className="flex flex-col items-center text-center">
          {(counterpartName || counterpartPhotoUrl) && (
            <Avatar url={counterpartPhotoUrl ?? null} name={counterpartName ?? ''} size="lg" />
          )}
          <h2 className="mt-3 font-heading text-xl font-bold text-text-primary">
            {counterpartName ? t('rating.titleNamed', { name: counterpartName }) : title}
          </h2>
          {tripSummary && (
            <p className="mt-1 text-sm font-medium text-text-secondary" data-testid="rating-trip">
              {tripSummary}
            </p>
          )}
        </div>

        <div className="mt-5 flex flex-col items-center gap-2">
          <StarRating value={stars} onChange={setStars} size="lg" disabled={busy} label={title} />
          {/* Reserved whether or not a star has been tapped, so the sheet does
              not jump under her thumb the moment she taps one. */}
          <p className="h-5 text-sm font-semibold text-primary" aria-live="polite" data-testid="rating-meaning">
            {stars === null ? '' : t(`rating.stars.${stars}`)}
          </p>
        </div>

        {/* One tap each, and none of them required. Almost nobody writes a
            comment, so without these a middling rating says nothing anybody
            can act on - and a question that costs a tap gets answered where
            a textarea does not. */}
        {offered.length > 0 && (
          <div className="mt-4">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">
              {band === 'positive' ? t('rating.whatWentWell') : t('rating.whatWentWrong')}
            </p>
            <div className="flex flex-wrap gap-2">
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
                      'rounded-full border px-3.5 py-2 text-sm font-medium transition-colors ' +
                      (chosen
                        ? 'border-primary bg-primary text-text-inverse'
                        : 'border-border bg-background text-text-primary')
                    }
                  >
                    {option.label}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {stars !== null && (
          <label className="mt-4 block">
            <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('rating.commentLabel')}</span>
            <textarea
              className="min-h-[76px] w-full rounded-input border border-border bg-background p-3 text-sm text-text-primary outline-none transition-colors focus:border-primary"
              maxLength={COMMENT_MAX}
              value={comment}
              disabled={busy}
              onChange={(e) => setComment(e.target.value)}
              placeholder={t('rating.commentPlaceholder', { who })}
            />
          </label>
        )}

        <p className="mt-4 text-center text-xs leading-relaxed text-text-secondary">{message}</p>

        {error && (
          <p className="mt-3 text-center text-sm text-danger" role="alert">
            {error}
          </p>
        )}

        <div className="mt-4 space-y-2">
          <Button
            fullWidth
            size="lg"
            disabled={stars === null || busy}
            onClick={() => stars !== null && onSubmit(stars, comment.trim() || undefined, tags)}
          >
            {busy ? t('common.sending') : t('rating.submit')}
          </Button>
          <button
            type="button"
            disabled={busy}
            onClick={onSkip}
            className="block w-full py-2.5 text-center text-sm font-semibold text-text-secondary disabled:opacity-50"
            data-testid="rating-skip"
          >
            {t('common.notNow')}
          </button>
        </div>
      </div>
    </Overlay>
  );
}

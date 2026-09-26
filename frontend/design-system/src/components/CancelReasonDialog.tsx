import { useEffect, useState } from 'react';
import { Button } from './Button';
import { Overlay } from './Overlay';
import {
  reasonRequiresNote,
  type CancellationReason,

} from '../lib/cancellation';
import { useTranslation } from 'react-i18next';

/**
 * Generic over the reason type, so the same picker serves every "tell us why"
 * - cancelling a trip, or ending one away from the drop. OTHER (and only
 * OTHER) asks for a note, in every use, matching the backend's rule.
 */
export interface CancelReasonDialogProps<R extends string = CancellationReason> {
  open: boolean;
  title?: string;
  message?: string;
  options: { value: R; label: string }[];
  /** Disables both buttons and shows progress while the request is in flight. */
  busy?: boolean;
  /** A failed request, shown in place rather than dismissing the dialog. */
  error?: string | null;
  onConfirm: (reason: R, note?: string) => void;
  onCancel: () => void;
  /** Button words, for uses other than cancelling. */
  keepLabel?: string;
  confirmLabel?: string;
  busyLabel?: string;
  /** Cancelling is destructive (red); ending a trip is not. */
  confirmVariant?: 'danger' | 'primary';
}

const NOTE_MAX = 500;

/**
 * Asks why, before cancelling.
 * <p>
 * Built on the same bones as ConfirmDialog - backdrop click and Escape both
 * get you out, body scroll locked, the safe way out on the left - because
 * this is the same kind of moment and should not feel like a different app.
 * The difference is that it cannot be confirmed on a single tap: a reason
 * has to be chosen first, and the confirm button stays disabled until one
 * is.
 * <p>
 * That is deliberate friction, and it is the cheapest kind. Cancelling used
 * to be one tap on a red button, which is how you end up with a
 * cancellation record nobody can interpret: an account with a high rate and
 * no way to tell whether she is dodging fares or being repeatedly abandoned
 * by partners who never turn up. One tap more buys the answer.
 * <p>
 * The dialog keeps the trip alive behind it. Backing out here does not
 * cancel anything, which is why "Keep trip" says so rather than saying
 * "Cancel" next to another button that also says cancel.
 */
export function CancelReasonDialog<R extends string = CancellationReason>({
  open,
  title: titleProp,
  message: messageProp,
  options,
  busy = false,
  error = null,
  onConfirm,
  onCancel,
  keepLabel,
  confirmLabel,
  busyLabel,
  confirmVariant = 'danger',
}: CancelReasonDialogProps<R>) {
  const { t } = useTranslation('ds');
  const title = titleProp ?? t('cancelDialog.title');
  const message = messageProp ?? t('cancelDialog.message');
  const [reason, setReason] = useState<R | null>(null);
  const [note, setNote] = useState('');

  // A fresh dialog every time it opens. Leaving the last attempt's reason
  // selected would let a mis-tap confirm an answer the person never gave.
  useEffect(() => {
    if (open) {
      setReason(null);
      setNote('');
    }
  }, [open]);

  const needsNote = reasonRequiresNote(reason as CancellationReason | null);
  const canConfirm = reason !== null && (!needsNote || note.trim().length > 0) && !busy;

  return (
    <Overlay
      className="px-4 py-6"
      align="sheet"
      label={title}
      open={open}
      onDismiss={busy ? undefined : onCancel}
    >
      <div className="max-h-[88vh] w-full overflow-y-auto rounded-card bg-surface p-5 shadow-card motion-safe:animate-sheet-up">
        <p className="font-heading text-lg font-semibold text-text-primary">{title}</p>
        <p className="mt-2 text-sm text-text-secondary">{message}</p>

        <div className="mt-4 space-y-2">
          {options.map((option) => {
            const selected = reason === option.value;
            return (
              <label
                key={option.value}
                className={[
                  'flex cursor-pointer items-center gap-3 rounded-input border px-4 py-3 transition-colors',
                  selected ? 'border-primary bg-background' : 'border-border bg-surface',
                ].join(' ')}
              >
                <input
                  type="radio"
                  name="cancellation-reason"
                  className="h-4 w-4 accent-primary"
                  checked={selected}
                  disabled={busy}
                  onChange={() => setReason(option.value)}
                />
                <span className="text-sm text-text-primary">{option.label}</span>
              </label>
            );
          })}
        </div>

        {needsNote && (
          <label className="mt-3 block">
            <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('cancelDialog.noteLabel')}</span>
            <textarea
              className="min-h-[80px] w-full rounded-input border border-border bg-surface p-3 text-sm text-text-primary outline-none transition-colors focus:border-primary"
              maxLength={NOTE_MAX}
              value={note}
              disabled={busy}
              onChange={(e) => setNote(e.target.value)}
              placeholder={t('cancelDialog.notePlaceholder')}
            />
          </label>
        )}

        {error && <p className="mt-3 text-sm text-danger">{error}</p>}

        <div className="mt-5 flex gap-3">
          <Button variant="secondary" fullWidth disabled={busy} onClick={onCancel}>
            {keepLabel ?? t('cancelDialog.keepTrip')}
          </Button>
          <Button
            variant={confirmVariant}
            fullWidth
            disabled={!canConfirm}
            onClick={() => reason && onConfirm(reason, needsNote ? note.trim() : undefined)}
          >
            {busy ? (busyLabel ?? t('cancelDialog.cancelling')) : (confirmLabel ?? t('cancelDialog.cancelTrip'))}
          </Button>
        </div>
      </div>
    </Overlay>
  );
}

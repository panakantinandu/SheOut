import { useEffect, useState } from 'react';
import { Button } from './Button';
import {
  reasonRequiresNote,
  type CancellationReason,
  type CancellationReasonOption,
} from '../lib/cancellation';

export interface CancelReasonDialogProps {
  open: boolean;
  title?: string;
  message?: string;
  options: CancellationReasonOption[];
  /** Disables both buttons and shows progress while the request is in flight. */
  busy?: boolean;
  /** A failed cancel, shown in place rather than dismissing the dialog. */
  error?: string | null;
  onConfirm: (reason: CancellationReason, note?: string) => void;
  onCancel: () => void;
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
export function CancelReasonDialog({
  open,
  title = 'Why are you cancelling?',
  message = 'This helps us understand what went wrong. Your answer is not shown to the other person.',
  options,
  busy = false,
  error = null,
  onConfirm,
  onCancel,
}: CancelReasonDialogProps) {
  const [reason, setReason] = useState<CancellationReason | null>(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel();
    };
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, busy, onCancel]);

  // A fresh dialog every time it opens. Leaving the last attempt's reason
  // selected would let a mis-tap confirm an answer the person never gave.
  useEffect(() => {
    if (open) {
      setReason(null);
      setNote('');
    }
  }, [open]);

  if (!open) return null;

  const needsNote = reasonRequiresNote(reason);
  const canConfirm = reason !== null && (!needsNote || note.trim().length > 0) && !busy;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-text-primary/40 px-4 py-6 sm:items-center"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={() => { if (!busy) onCancel(); }}
    >
      <div
        className="max-h-full w-full max-w-sm overflow-y-auto rounded-card bg-surface p-5 shadow-card"
        onClick={(e) => e.stopPropagation()}
      >
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
            <span className="mb-1.5 block text-sm font-medium text-text-primary">Tell us what happened</span>
            <textarea
              className="min-h-[80px] w-full rounded-input border border-border bg-surface p-3 text-sm text-text-primary outline-none transition-colors focus:border-primary"
              maxLength={NOTE_MAX}
              value={note}
              disabled={busy}
              onChange={(e) => setNote(e.target.value)}
              placeholder="A sentence is enough"
            />
          </label>
        )}

        {error && <p className="mt-3 text-sm text-danger">{error}</p>}

        <div className="mt-5 flex gap-3">
          <Button variant="secondary" fullWidth disabled={busy} onClick={onCancel}>
            Keep trip
          </Button>
          <Button
            variant="danger"
            fullWidth
            disabled={!canConfirm}
            onClick={() => reason && onConfirm(reason, needsNote ? note.trim() : undefined)}
          >
            {busy ? 'Cancelling...' : 'Cancel trip'}
          </Button>
        </div>
      </div>
    </div>
  );
}

import { useEffect } from 'react';
import { Button } from './Button';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Renders the confirm button in the danger colour, for destructive actions. */
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * A blocking yes/no prompt for actions that should not fire on a single
 * stray tap - logging out, most obviously, which previously ended the
 * session the instant the row was touched with no way back.
 * <p>
 * Deliberately not window.confirm: that is unstyled, says "localhost
 * says", and is suppressed outright in some in-app browsers, which would
 * silently turn a guarded action back into an unguarded one.
 * <p>
 * Escape cancels and the backdrop is clickable, so the safe way out is
 * always the easy one. Body scroll is locked while open so the page
 * behind cannot be moved under the dialog.
 */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-text-primary/40 px-6"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onCancel}
    >
      <div
        className="w-full max-w-xs rounded-card bg-surface p-5 shadow-card"
        // The backdrop closes the dialog; clicks inside it must not bubble
        // up and close it too.
        onClick={(e) => e.stopPropagation()}
      >
        <p className="font-heading text-lg font-semibold text-text-primary">{title}</p>
        {message && <p className="mt-2 text-sm text-text-secondary">{message}</p>}
        <div className="mt-5 flex gap-3">
          <Button variant="secondary" fullWidth onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button variant={destructive ? 'danger' : 'primary'} fullWidth onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}

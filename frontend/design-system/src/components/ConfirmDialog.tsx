import { Button } from './Button';
import { Overlay } from './Overlay';
import { useTranslation } from 'react-i18next';

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
  confirmLabel,
  cancelLabel,
  destructive = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const { t } = useTranslation('ds');

  return (
    <Overlay open={open} label={title} onDismiss={onCancel} className="px-6">
      <div className="w-full max-w-xs rounded-card bg-surface p-5 shadow-card motion-safe:animate-pop-in">
        <p className="font-heading text-lg font-semibold text-text-primary">{title}</p>
        {message && <p className="mt-2 max-h-[55vh] overflow-y-auto whitespace-pre-line text-sm text-text-secondary">{message}</p>}
        <div className="mt-5 flex gap-3">
          <Button variant="secondary" fullWidth onClick={onCancel}>
            {cancelLabel ?? t('common.cancel')}
          </Button>
          <Button variant={destructive ? 'danger' : 'primary'} fullWidth onClick={onConfirm}>
            {confirmLabel ?? t('common.confirm')}
          </Button>
        </div>
      </div>
    </Overlay>
  );
}

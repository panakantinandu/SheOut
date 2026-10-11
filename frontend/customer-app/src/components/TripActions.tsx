import { Headphones, MessageCircle, Radio, XCircle } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from '@sheout/design-system';

/**
 * What she can do about her trip, in one row of equal buttons on the card
 * that describes it: Chat, Share, Support, and Cancel while she still can.
 * <p>
 * Replaces a pinned bar at the bottom of the screen, where Cancel was a 44 px
 * pill beside the phone's gesture bar - easy to miss when she looked for it,
 * easy to hit when she reached for Share. Here it sits next to the partner
 * it is about, found without scrolling at 360 px, and is never the button a
 * thumb lands on by accident: it is last in the row, outlined rather than
 * filled, and it opens a reason sheet before anything is cancelled.
 * <p>
 * Icon over a short label, at least 56 px tall, so four fit at 360 px in
 * Hindi and Telugu without wrapping or shrinking a target.
 */
export function TripActions({
  onChat,
  onShare,
  onSupport,
  onCancel,
  cancelLabel,
  cancelBusy = false,
}: {
  /** Absent until a partner has accepted: there is nobody to chat with. */
  onChat?: () => void;
  onShare: () => void;
  /** Absent when no support number is configured. */
  onSupport?: () => void;
  /** Absent once the trip has started: a trip under way is not cancelled from here. */
  onCancel?: () => void;
  /** The full name for screen readers - "Cancel ride" or "Cancel delivery". */
  cancelLabel: string;
  cancelBusy?: boolean;
}) {
  const { t } = useTranslation();
  const count = [onChat, onShare, onSupport, onCancel].filter(Boolean).length;
  const cols = count >= 4 ? 'grid-cols-4' : count === 3 ? 'grid-cols-3' : count === 2 ? 'grid-cols-2' : 'grid-cols-1';
  return (
    <div role="group" aria-label={t('tracking.actions.label')} className={`grid ${cols} gap-2`} data-testid="trip-actions">
      {onChat && (
        <Action icon={<MessageCircle />} label={t('tracking.actions.chat')} aria={t('tracking.messagePartner')} onClick={onChat} testId="trip-chat" />
      )}
      <Action icon={<Radio />} label={t('tracking.actions.share')} aria={t('tracking.shareTrip')} onClick={onShare} testId="share-trip" />
      {onSupport && (
        <Action icon={<Headphones />} label={t('tracking.actions.support')} aria={t('tracking.support')} onClick={onSupport} testId="trip-support" />
      )}
      {onCancel && (
        <Action icon={<XCircle />} label={t('tracking.actions.cancel')} aria={cancelLabel} onClick={onCancel} testId="cancel-ride" tone="cancel" disabled={cancelBusy} />
      )}
    </div>
  );
}

function Action({ icon, label, aria, onClick, testId, tone = 'neutral', disabled = false }: {
  icon: ReactNode;
  label: string;
  aria: string;
  onClick: () => void;
  testId: string;
  tone?: 'neutral' | 'cancel';
  disabled?: boolean;
}) {
  const toneClasses = tone === 'cancel'
    ? 'border border-danger/35 bg-surface text-danger'
    : 'border border-transparent bg-background text-text-primary';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={aria}
      data-testid={testId}
      className={`flex min-h-14 min-w-0 flex-col items-center justify-center gap-1 rounded-input px-1 py-2 text-xs font-semibold transition-colors active:bg-primary-light disabled:opacity-60 ${toneClasses}`}
    >
      <span className={`[&>svg]:h-5 [&>svg]:w-5 ${tone === 'cancel' ? 'text-danger' : 'text-primary'}`} aria-hidden="true">{icon}</span>
      <span className="max-w-full truncate leading-tight">{label}</span>
    </button>
  );
}

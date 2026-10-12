import { Clock, Navigation, PackageX, Undo2, UserCheck } from 'lucide-react';
import { useState } from 'react';
import { Button, PICKUP_CODE_LENGTH, PickupCodeField, TextField, useTranslation } from '@sheout/design-system';
import { ApiError, parcelApi } from '../api/client';
import type { ParcelExceptionReason, ParcelView } from '../api/types';
import { apiErrorText } from '../lib/apiErrors';
import { ParcelPhotoCapture } from './ParcelPhotoCapture';

const REASONS: ParcelExceptionReason[] = [
  'RECEIVER_NOT_THERE', 'RECEIVER_NOT_ANSWERING', 'RECEIVER_REFUSED', 'RECEIVER_HAS_NO_CODE', 'ADDRESS_NOT_FOUND', 'OTHER',
];

/**
 * The drop, for a parcel. A delivery ends on the receiver's code, which only
 * the sender could have given her, after a photo of the handover - or, when
 * nobody can take it, on the sender's own answer: wait, leave it with someone
 * she names, or bring it back. Never on the partner's word alone.
 */
export function ParcelDropStep({ bookingId, parcel, atDropOff, busy, codeError, onParcel, onMarkDelivered, onNavigate }: {
  bookingId: string;
  parcel: ParcelView;
  atDropOff: boolean;
  busy: boolean;
  codeError: string | null;
  onParcel: (p: ParcelView) => void;
  /** Ends the trip - with the code, or without one when the sender chose "leave it with". */
  onMarkDelivered: (code?: string) => void;
  onNavigate: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const [exceptionOpen, setExceptionOpen] = useState(false);
  const [reason, setReason] = useState<ParcelExceptionReason | null>(null);
  const [note, setNote] = useState('');
  const [sending, setSending] = useState(false);
  const [exceptionError, setExceptionError] = useState<string | null>(null);
  const [exceptionPhoto, setExceptionPhoto] = useState(false);

  const leaveWith = parcel.senderChoice === 'LEAVE_WITH';
  const returning = parcel.senderChoice === 'RETURN';
  const photoOk = !parcel.photosRequired || parcel.deliveryPhotoTaken;
  const first = parcel.receiverName;

  async function sendException() {
    if (!reason) return;
    setSending(true);
    setExceptionError(null);
    try {
      onParcel(await parcelApi.raiseException(bookingId, reason, note.trim() || undefined));
      setExceptionOpen(false);
      setReason(null);
      setNote('');
      setExceptionPhoto(false);
    } catch (err) {
      setExceptionError(apiErrorText(err, 'trip.completeError'));
      if (err instanceof ApiError && err.body?.error === 'PARCEL_EXCEPTION_PHOTO_REQUIRED') setExceptionPhoto(false);
    } finally {
      setSending(false);
    }
  }

  if (parcel.awaitingSenderChoice) {
    return (
      <p className="flex items-start gap-2 rounded-input bg-accent-orange/10 px-3 py-3 text-sm font-medium text-text-primary" role="status" data-testid="parcel-waiting-sender">
        <Clock className="mt-0.5 h-4 w-4 shrink-0 text-accent-orange" aria-hidden="true" />
        {t('trip.parcel.waitingForSender')}
      </p>
    );
  }

  if (exceptionOpen) {
    return (
      <div className="space-y-3 rounded-card border-2 border-accent-orange/40 p-4" data-testid="parcel-exception-form">
        <p className="font-heading text-card-title text-text-primary">{t('trip.parcel.exceptionTitle')}</p>
        <div className="space-y-2" role="radiogroup" aria-label={t('trip.parcel.exceptionTitle')}>
          {REASONS.map((r) => (
            <label key={r} className={`flex min-h-tap cursor-pointer items-center gap-3 rounded-input border px-3 py-2 text-sm ${reason === r ? 'border-primary bg-primary-light/40' : 'border-border'}`}>
              <input type="radio" name="parcel-exception" className="h-5 w-5 accent-primary" checked={reason === r} onChange={() => setReason(r)} />
              {t(`trip.parcel.reason${r}`)}
            </label>
          ))}
        </div>
        {reason === 'OTHER' && (
          <TextField label={t('trip.parcel.exceptionNote')} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} data-testid="parcel-exception-note" />
        )}
        <ParcelPhotoCapture bookingId={bookingId} kind="EXCEPTION" taken={exceptionPhoto} onSaved={(p) => { setExceptionPhoto(true); onParcel(p); }} />
        {exceptionError && <p className="text-sm text-danger" role="alert">{exceptionError}</p>}
        <Button fullWidth disabled={!reason || sending || (reason === 'OTHER' && !note.trim()) || (parcel.photosRequired && !exceptionPhoto)} onClick={sendException} data-testid="parcel-exception-send">
          {t('trip.parcel.exceptionSend')}
        </Button>
        <Button fullWidth variant="secondary" size="md" disabled={sending} onClick={() => setExceptionOpen(false)}>
          {t('trip.parcel.back')}
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-3" data-testid="parcel-drop-step">
      {/* What the sender decided, when she was asked. */}
      {parcel.senderChoice === 'WAIT' && (
        <p className="flex items-start gap-2 rounded-input bg-background px-3 py-2 text-sm text-text-primary" data-testid="parcel-sender-wait">
          <Clock className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />{t('trip.parcel.senderWait')}
        </p>
      )}
      {leaveWith && (
        <p className="flex items-start gap-2 rounded-input bg-primary-light/40 px-3 py-2 text-sm font-medium text-text-primary" data-testid="parcel-sender-leave-with">
          <UserCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />{t('trip.parcel.leaveWith', { name: parcel.leaveWithName })}
        </p>
      )}
      {returning && (
        <p className="flex items-start gap-2 rounded-input bg-primary-light/40 px-3 py-2 text-sm font-medium text-text-primary" data-testid="parcel-sender-return">
          <Undo2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />{t('trip.parcel.returnToSender')}
        </p>
      )}

      {!atDropOff && (
        <Button fullWidth icon={<Navigation className="h-5 w-5" />} onClick={onNavigate} data-testid="start-navigation">
          {t('trip.navigateToDrop')}
        </Button>
      )}

      <ParcelPhotoCapture bookingId={bookingId} kind="DELIVERY" taken={parcel.deliveryPhotoTaken} onSaved={onParcel} />

      {!leaveWith && (
        <div className="space-y-2">
          <p className="text-sm text-text-primary">{returning ? t('trip.parcel.codeAskSender') : t('trip.parcel.codeAsk', { name: first })}</p>
          <PickupCodeField
            value={code}
            onChange={setCode}
            error={codeError ?? undefined}
            disabled={busy}
            onSubmit={() => photoOk && onMarkDelivered(code)}
            label={t('trip.parcel.codeLabel')}
            placeholder={t('trip.parcel.codePlaceholder')}
          />
          {parcel.codeAttemptsLeft < 5 && (
            <p className="text-caption text-text-secondary" data-testid="parcel-attempts-left">{t('trip.parcel.attemptsLeft', { count: parcel.codeAttemptsLeft })}</p>
          )}
        </div>
      )}

      <Button
        fullWidth
        variant="success"
        disabled={busy || !photoOk || (!leaveWith && code.length !== PICKUP_CODE_LENGTH)}
        onClick={() => onMarkDelivered(leaveWith ? undefined : code)}
        data-testid="parcel-mark-delivered"
      >
        {busy ? t('trip.parcel.marking') : t('trip.parcel.markDelivered')}
      </Button>

      {!leaveWith && (
        <Button fullWidth variant="secondary" size="md" icon={<PackageX className="h-4 w-4" />} disabled={busy} onClick={() => { setExceptionError(null); setExceptionOpen(true); }} data-testid="parcel-cant-hand-over">
          {t('trip.parcel.cantHandOver')}
        </Button>
      )}
    </div>
  );
}

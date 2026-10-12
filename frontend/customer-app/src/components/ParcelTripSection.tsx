import { Camera, KeyRound, Package, Share2, TriangleAlert } from 'lucide-react';
import { useEffect, useState } from 'react';
import { AmountText, Button, Card, IconCircle, TextField, useTranslation } from '@sheout/design-system';
import { parcelApi } from '../api/client';
import type { BookingStatus, ParcelSenderChoice, ParcelView } from '../api/types';
import { shareViaDevice } from '../lib/emergency';

const POLL_MS = 5000;

/**
 * A parcel's own details, in their own card under the trip card, so the card
 * a ride and a parcel share stays the same shape for both: who it is for and
 * what is in it, the delivery code to pass on, the partner's question when
 * she cannot hand it over, and the photos.
 * <p>
 * The code is shown from the moment a partner accepts, so the sender can
 * send it to the receiver before the parcel is even collected; it is the
 * one thing that lets the partner mark it delivered.
 */
export function ParcelTripSection({ bookingId, status, note }: { bookingId: string; status: BookingStatus; note: string | null | undefined }) {
  const { t } = useTranslation();
  const [parcel, setParcel] = useState<ParcelView | null>(null);
  const [shareNote, setShareNote] = useState<string | null>(null);
  const [leaveWithOpen, setLeaveWithOpen] = useState(false);
  const [leaveWithName, setLeaveWithName] = useState('');
  const [choosing, setChoosing] = useState(false);
  const [choiceError, setChoiceError] = useState<string | null>(null);
  const live = status === 'REQUESTED' || status === 'MATCHED' || status === 'ACCEPTED' || status === 'IN_PROGRESS';

  useEffect(() => {
    let cancelled = false;
    const load = () => parcelApi.get(bookingId).then((p) => { if (!cancelled) setParcel(p); }).catch(() => {
      // A parcel booked before proof of delivery has no record: the card shows its note only.
    });
    load();
    if (!live) return () => { cancelled = true; };
    const interval = setInterval(load, POLL_MS);
    return () => { cancelled = true; clearInterval(interval); };
  }, [bookingId, live]);

  async function shareCode(code: string) {
    const text = t('tracking.parcel.shareCodeText', { code });
    const outcome = await shareViaDevice(t('tracking.parcel.codeTitle'), text, '');
    if (outcome === 'unsupported') {
      try {
        await navigator.clipboard?.writeText(text);
        setShareNote(t('tracking.parcel.codeCopied'));
      } catch {
        setShareNote(text);
      }
    }
  }

  async function choose(choice: ParcelSenderChoice) {
    setChoosing(true);
    setChoiceError(null);
    try {
      setParcel(await parcelApi.choose(bookingId, choice, choice === 'LEAVE_WITH' ? leaveWithName.trim() : undefined));
      setLeaveWithOpen(false);
    } catch {
      setChoiceError(t('tracking.parcel.choiceError'));
    } finally {
      setChoosing(false);
    }
  }

  const returning = parcel?.senderChoice === 'RETURN';
  const onTrip = status === 'IN_PROGRESS';

  return (
    <Card className="space-y-3" data-testid="parcel-section">
      <div className="flex items-center gap-3">
        <IconCircle size="md" tone="soft" color="orange" icon={<Package />} />
        <div className="min-w-0">
          <p className="font-heading text-card-title text-text-primary">{t('tracking.parcel.title')}</p>
          {parcel && (
            <p className="truncate text-sm text-text-secondary" data-testid="parcel-receiver">
              {t('tracking.parcel.receiver')} {parcel.receiverName}
            </p>
          )}
        </div>
      </div>

      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-text-secondary">{t('tracking.parcel.contents')}</p>
        <p className="line-clamp-3 text-sm text-text-primary" data-testid="parcel-section-note">
          {note?.trim() ? note : t('tracking.parcel.noNote')}
        </p>
      </div>

      {/* She cannot hand it over: the sender decides. */}
      {parcel?.awaitingSenderChoice && (
        <div className="space-y-3 rounded-card border-2 border-accent-orange/40 bg-accent-orange/5 p-4" role="alert" data-testid="parcel-exception">
          <p className="flex items-center gap-2 font-heading text-card-title text-text-primary">
            <TriangleAlert className="h-5 w-5 shrink-0 text-accent-orange" aria-hidden="true" />
            {t('tracking.parcel.exceptionTitle')}
          </p>
          {parcel.exceptionReason && (
            <p className="text-sm text-text-primary">
              {t(`tracking.parcel.reason${parcel.exceptionReason}`)}{parcel.exceptionNote ? `: ${parcel.exceptionNote}` : ''}
            </p>
          )}
          <p className="text-sm font-semibold text-text-primary">{t('tracking.parcel.choose')}</p>
          <Button fullWidth variant="secondary" disabled={choosing} onClick={() => choose('WAIT')} data-testid="parcel-choice-wait">
            {t('tracking.parcel.wait')}
          </Button>
          {leaveWithOpen ? (
            <div className="space-y-2">
              <TextField label={t('tracking.parcel.leaveWithName')} maxLength={80} value={leaveWithName} onChange={(e) => setLeaveWithName(e.target.value)} data-testid="parcel-leave-with-name" />
              <Button fullWidth disabled={choosing || !leaveWithName.trim()} onClick={() => choose('LEAVE_WITH')} data-testid="parcel-choice-leave-with">
                {t('tracking.parcel.leaveWithConfirm')}
              </Button>
            </div>
          ) : (
            <Button fullWidth variant="secondary" disabled={choosing} onClick={() => setLeaveWithOpen(true)} data-testid="parcel-choice-leave-with-open">
              {t('tracking.parcel.leaveWith')}
            </Button>
          )}
          <div>
            <Button fullWidth variant="secondary" disabled={choosing} onClick={() => choose('RETURN')} data-testid="parcel-choice-return">
              {t('tracking.parcel.returnIt')}
            </Button>
            <p className="mt-1 flex items-center gap-1 text-caption text-text-secondary">
              {t('tracking.parcel.returnNote', { fare: Math.round(parcel.currentFare) })}
            </p>
          </div>
          {choiceError && <p className="text-sm text-danger">{choiceError}</p>}
        </div>
      )}

      {parcel && !parcel.awaitingSenderChoice && parcel.senderChoice === 'WAIT' && onTrip && (
        <p className="rounded-input bg-background px-3 py-2 text-sm text-text-secondary" data-testid="parcel-waiting">{t('tracking.parcel.waiting')}</p>
      )}
      {returning && onTrip && (
        <p className="flex items-center justify-between gap-2 rounded-input bg-background px-3 py-2 text-sm text-text-secondary" data-testid="parcel-returning">
          <span>{t('tracking.parcel.returning')}</span>
          {parcel?.returnFare != null && <AmountText amount={parcel.returnFare} size="sm" />}
        </p>
      )}

      {/* The receiver's code, hers to pass on - the only way her partner can mark it delivered. */}
      {parcel?.deliveryCode && (
        <div className="space-y-2 rounded-card border-2 border-primary/25 bg-primary-light/40 p-4" data-testid="delivery-code">
          <p className="flex items-center gap-2 text-sm font-semibold text-text-primary">
            <KeyRound className="h-4 w-4 text-primary" aria-hidden="true" />
            {t('tracking.parcel.codeTitle')}
          </p>
          <p className="font-heading text-[2rem] font-bold leading-none tracking-[0.3em] text-text-primary" data-testid="delivery-code-digits">
            {parcel.deliveryCode}
          </p>
          <p className="text-sm text-text-secondary">
            {returning ? t('tracking.parcel.codeHintReturn') : t('tracking.parcel.codeHint', { name: parcel.receiverName.split(' ')[0] })}
          </p>
          {!returning && (
            <Button fullWidth variant="secondary" size="md" icon={<Share2 className="h-4 w-4" />} onClick={() => shareCode(parcel.deliveryCode!)} data-testid="delivery-code-share">
              {t('tracking.parcel.shareCode')}
            </Button>
          )}
          {shareNote && <p className="text-xs text-text-secondary" data-testid="delivery-code-note">{shareNote}</p>}
        </div>
      )}

      {parcel?.delivered && <p className="text-sm font-semibold text-accent-green-strong" data-testid="parcel-delivered">{t('tracking.parcel.delivered')}</p>}
      {status === 'COMPLETED' && parcel?.senderChoice === 'LEAVE_WITH' && parcel.leaveWithName && (
        <p className="text-sm font-semibold text-accent-green-strong" data-testid="parcel-left-with">{t('tracking.parcel.leftWith', { name: parcel.leaveWithName })}</p>
      )}

      {!onTrip && live && !parcel?.deliveryCode && (
        <p className="rounded-input bg-background px-3 py-2 text-sm text-text-secondary">{t('tracking.parcel.handover')}</p>
      )}
      {onTrip && !parcel?.awaitingSenderChoice && !returning && parcel?.senderChoice !== 'WAIT' && (
        <p className="rounded-input bg-background px-3 py-2 text-sm text-text-secondary">{t('tracking.parcel.onTheWay')}</p>
      )}

      {parcel && parcel.photos.length > 0 && (
        <div data-testid="parcel-photos">
          <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-text-secondary">
            <Camera className="h-3.5 w-3.5" aria-hidden="true" />
            {t('tracking.parcel.photos')}
          </p>
          <div className="grid grid-cols-3 gap-2">
            {parcel.photos.map((photo, i) => (
              <figure key={`${photo.kind}-${i}`} className="min-w-0">
                {photo.url ? (
                  <a href={photo.url} target="_blank" rel="noopener noreferrer" className="block">
                    <img src={photo.url} alt={t(`tracking.parcel.photo${photo.kind}`)} className="aspect-square w-full rounded-input object-cover" loading="lazy" />
                  </a>
                ) : (
                  <div className="flex aspect-square w-full items-center justify-center rounded-input bg-background p-1 text-center text-[11px] text-text-secondary">
                    {t('tracking.parcel.photoGone')}
                  </div>
                )}
                <figcaption className="mt-1 truncate text-[11px] text-text-secondary">{t(`tracking.parcel.photo${photo.kind}`)}</figcaption>
              </figure>
            ))}
          </div>
        </div>
      )}
    </Card>
  );
}

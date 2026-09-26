import { Phone, ShieldAlert } from 'lucide-react';
import { useState } from 'react';
import { Button, Overlay, SafetyText, useTranslation } from '@sheout/design-system';
import { dispatchApi } from '../api/client';
import { readPositionOnce } from '../lib/LocationBroadcastContext';

type SendState = 'idle' | 'sending' | 'sent' | 'failed';

/**
 * SOS for a partner.
 * <p>
 * The rider app has had one from the start; the partner app had none, and a
 * partner is the one out alone on a night road, trip after trip. Two ways out,
 * in the order that matters: 112 first, because nothing SheOut does reaches
 * her faster than the police; then the SheOut safety team, who get her
 * position and are paged on every operator device.
 * <p>
 * Always reachable, trip or not - she may need it on the way home.
 */
export function PartnerSos({ bookingId, position }: {
  bookingId?: string;
  /** Her live position when the screen already has one; read afresh otherwise. */
  position?: { lat: number; lng: number } | null;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<SendState>('idle');

  async function alertTeam() {
    setState('sending');
    const here = await readPositionOnce(position ?? null);
    if (!here) {
      setState('failed');
      return;
    }
    try {
      await dispatchApi.triggerSos({ lat: here.lat, lng: here.lng, bookingId });
      setState('sent');
    } catch {
      setState('failed');
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => { setState('idle'); setOpen(true); }}
        className="inline-flex min-h-[44px] items-center gap-1.5 rounded-full bg-danger px-4 text-sm font-semibold text-text-inverse shadow-card"
        data-testid="partner-sos"
      >
        <ShieldAlert className="h-4 w-4" aria-hidden />
        {t('sos.button')}
      </button>

      <Overlay open={open} label={t('sos.title')} onDismiss={() => setOpen(false)} className="px-6">
        <div className="w-full max-w-sm space-y-4 rounded-card bg-surface p-5 shadow-card motion-safe:animate-pop-in">
          <p className="font-heading text-lg font-semibold text-text-primary">
            <SafetyText k="sos.title" />
          </p>
          <p className="text-sm text-text-secondary"><SafetyText k="sos.body" /></p>

          <a
            href="tel:112"
            className="flex min-h-[48px] w-full items-center justify-center gap-2 rounded-full bg-danger font-semibold text-text-inverse"
          >
            <Phone className="h-5 w-5" aria-hidden />
            <SafetyText k="sos.call112" englishClassName="inline ml-1" />
          </a>

          <Button
            fullWidth
            variant="secondary"
            disabled={state === 'sending' || state === 'sent'}
            onClick={alertTeam}
          >
            {state === 'sending' ? t('sos.sending') : t('sos.alertTeam')}
          </Button>

          {state === 'sent' && (
            <p className="text-sm font-medium text-accent-green" role="status"><SafetyText k="sos.sent" /></p>
          )}
          {state === 'failed' && (
            <p className="text-sm font-medium text-danger" role="alert"><SafetyText k="sos.failed" /></p>
          )}

          <Button fullWidth variant="secondary" onClick={() => setOpen(false)}>
            {t('sos.close')}
          </Button>
        </div>
      </Overlay>
    </>
  );
}

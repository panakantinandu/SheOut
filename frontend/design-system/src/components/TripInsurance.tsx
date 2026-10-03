import { ExternalLink, Phone, ShieldCheck, Siren } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';
import { Overlay } from './Overlay';

/**
 * The cover a trip had, as both apps receive it from
 * GET /api/v1/insurance/trips/{id}. There is no premium in it on purpose:
 * what SheOut pays for cover is its own cost, never part of a fare.
 */
export interface TripCoverInfo {
  insurerName: string;
  policyNumber: string;
  sumInsured: number;
  coverageSummary: string | null;
  claimSteps: string | null;
  claimsPhone: string | null;
  claimsUrl: string | null;
  policySummaryUrl: string | null;
}

/** "₹5,00,000" - Indian grouping, no paise. */
export function formatSumInsured(amount: number): string {
  return `₹${Math.round(amount).toLocaleString('en-IN')}`;
}

/**
 * "Insured trip · ₹5,00,000 cover". Render it only with a cover the server
 * returned for this trip: the chip is a claim SheOut makes to a woman about
 * her safety, and it is made only from a record that exists.
 */
export function InsuredTripChip({ cover, onOpen }: { cover: TripCoverInfo; onOpen: () => void }) {
  const { t } = useTranslation('ds');
  return (
    <button
      type="button"
      onClick={onOpen}
      className="inline-flex items-center gap-1.5 rounded-chip border border-accent-green/30 bg-accent-green-tint px-3 py-1 text-xs font-semibold text-accent-green-strong"
      data-testid="insured-trip-chip"
    >
      <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
      {t('insurance.chip', { amount: formatSumInsured(cover.sumInsured) })}
    </button>
  );
}

/** What the chip opens: who insures the trip, under which policy, what is covered and how to claim. */
export function TripCoverSheet({
  open,
  cover,
  onClose,
  onReportAccident,
}: {
  open: boolean;
  cover: TripCoverInfo | null;
  onClose: () => void;
  /** Omit where an accident cannot be reported from (a trip that never started). */
  onReportAccident?: () => void;
}) {
  const { t } = useTranslation('ds');
  return (
    <Overlay open={open && Boolean(cover)} label={t('insurance.sheetTitle')} onDismiss={onClose} align="sheet">
      {cover && (
        <div className="max-h-[85vh] w-full max-w-md overflow-y-auto rounded-t-card bg-surface p-5 shadow-overlay motion-safe:animate-sheet-up" data-testid="trip-cover-sheet">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-accent-green-strong" aria-hidden="true" />
            <p className="font-heading text-section text-text-primary">{t('insurance.sheetTitle')}</p>
          </div>
          <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="text-text-secondary">{t('insurance.insurer')}</dt>
            <dd className="font-medium text-text-primary">{cover.insurerName}</dd>
            <dt className="text-text-secondary">{t('insurance.policyNumber')}</dt>
            <dd className="font-medium text-text-primary">{cover.policyNumber}</dd>
            <dt className="text-text-secondary">{t('insurance.sumInsured')}</dt>
            <dd className="font-medium text-text-primary">{formatSumInsured(cover.sumInsured)}</dd>
          </dl>
          {cover.coverageSummary && (
            <div className="mt-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-text-secondary">{t('insurance.covered')}</p>
              <p className="mt-1 whitespace-pre-line text-sm text-text-primary">{cover.coverageSummary}</p>
            </div>
          )}
          <div className="mt-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-text-secondary">{t('insurance.howToClaim')}</p>
            {cover.claimSteps && <p className="mt-1 whitespace-pre-line text-sm text-text-primary">{cover.claimSteps}</p>}
            <div className="mt-2 flex flex-wrap gap-3 text-sm">
              {cover.claimsPhone && (
                <a href={`tel:${cover.claimsPhone}`} className="inline-flex items-center gap-1 font-medium text-primary">
                  <Phone className="h-4 w-4" aria-hidden="true" /> {t('insurance.call', { phone: cover.claimsPhone })}
                </a>
              )}
              {cover.claimsUrl && (
                <a href={cover.claimsUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-medium text-primary">
                  {t('insurance.claimsPage')} <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
                </a>
              )}
              {cover.policySummaryUrl && (
                <a href={cover.policySummaryUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-medium text-primary">
                  {t('insurance.policySummary')} <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
                </a>
              )}
            </div>
          </div>
          <p className="mt-4 text-xs text-text-secondary">{t('insurance.paidBySheOut')}</p>
          <p className="mt-1 text-xs text-text-secondary">{t('insurance.insurerDecides')}</p>
          <div className="mt-5 flex flex-col gap-2">
            {onReportAccident && (
              <Button variant="secondary" fullWidth onClick={onReportAccident} data-testid="report-accident-open">
                {t('insurance.reportAccident')}
              </Button>
            )}
            <Button fullWidth onClick={onClose}>
              {t('common.close')}
            </Button>
          </div>
        </div>
      )}
    </Overlay>
  );
}

/**
 * "Report an accident / make a claim". Sends what happened, raises a
 * high-priority ticket on the trip, and shows the insurer's own claim steps
 * when the trip had cover. 112 is said first, before any form: a form is not
 * how somebody who is hurt gets help.
 */
export function AccidentReportSheet({
  open,
  onClose,
  submit,
}: {
  open: boolean;
  onClose: () => void;
  submit: (description: string) => Promise<{ ticketId: string; cover: TripCoverInfo | null }>;
}) {
  const { t } = useTranslation('ds');
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{ ticketId: string; cover: TripCoverInfo | null } | null>(null);

  async function send() {
    setSending(true);
    setError(null);
    try {
      setResult(await submit(text.trim()));
    } catch (err) {
      setError(err instanceof Error ? err.message : t('accident.error'));
    } finally {
      setSending(false);
    }
  }

  function close() {
    setText('');
    setResult(null);
    setError(null);
    onClose();
  }

  return (
    <Overlay open={open} label={t('accident.title')} onDismiss={close} align="sheet">
      <div className="max-h-[85vh] w-full max-w-md overflow-y-auto rounded-t-card bg-surface p-5 shadow-overlay motion-safe:animate-sheet-up" data-testid="accident-sheet">
        <p className="font-heading text-section text-text-primary">{t('accident.title')}</p>
        <a href="tel:112" className="mt-3 flex items-center gap-2 rounded-card bg-danger/10 p-3 text-sm font-semibold text-danger">
          <Siren className="h-4 w-4" aria-hidden="true" /> {t('accident.emergency')}
        </a>
        {result ? (
          <div className="mt-4 space-y-3 text-sm" data-testid="accident-sent">
            <p className="text-text-primary">{t('accident.sent')}</p>
            {result.cover ? (
              <div className="space-y-1 rounded-card bg-background p-3">
                <p className="font-medium text-text-primary">{t('accident.insurerSteps', { insurer: result.cover.insurerName })}</p>
                {result.cover.claimSteps && <p className="whitespace-pre-line text-text-secondary">{result.cover.claimSteps}</p>}
                <p className="text-text-secondary">{t('insurance.policyNumber')}: {result.cover.policyNumber}</p>
                {result.cover.claimsPhone && (
                  <a href={`tel:${result.cover.claimsPhone}`} className="inline-flex items-center gap-1 font-medium text-primary">
                    <Phone className="h-4 w-4" aria-hidden="true" /> {t('insurance.call', { phone: result.cover.claimsPhone })}
                  </a>
                )}
                <p className="text-xs text-text-secondary">{t('insurance.insurerDecides')}</p>
              </div>
            ) : (
              <p className="text-text-secondary">{t('accident.noCover')}</p>
            )}
            <Button fullWidth onClick={close}>
              {t('common.close')}
            </Button>
          </div>
        ) : (
          <div className="mt-4 space-y-3">
            <label className="block text-sm font-medium text-text-primary" htmlFor="accident-text">
              {t('accident.describe')}
            </label>
            <textarea
              id="accident-text"
              className="min-h-[110px] w-full rounded-input border border-border bg-surface p-3 text-sm text-text-primary"
              maxLength={1500}
              value={text}
              placeholder={t('accident.placeholder')}
              onChange={(e) => setText(e.target.value)}
            />
            {error && <p className="text-xs text-danger">{error}</p>}
            <div className="flex gap-2">
              <Button variant="secondary" fullWidth onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button fullWidth disabled={sending || !text.trim()} onClick={send} data-testid="accident-send">
                {sending ? t('accident.sending') : t('accident.send')}
              </Button>
            </div>
          </div>
        )}
      </div>
    </Overlay>
  );
}

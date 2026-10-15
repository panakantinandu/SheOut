import { useEffect, useState } from 'react';
import { Button, Card, COURT_SCREEN_CONSENT, highlightPlaceholders, useTranslation } from '@sheout/design-system';
import { ApiError, verificationApi } from '../api/client';
import type { CourtScreeningView } from '../api/types';

/**
 * Her court-record screening, if a reviewer asked for one: the consent to
 * give, where it stands, and - when a record may match her name - the chance
 * to answer. Nothing at all when there is no screening or the feature is off
 * (the server answers 404 either way).
 * <p>
 * The words are neutral on purpose. A shared name is common; she is never
 * told a case was "found against" her, never shown a case, and never told she
 * is "police verified" because the check passed.
 */
export function CourtScreeningCard() {
  const { t } = useTranslation();
  const [view, setView] = useState<CourtScreeningView | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [answer, setAnswer] = useState('');
  const [fullText, setFullText] = useState(false);
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);

  useEffect(() => {
    verificationApi
      .getCourtScreening()
      .then(setView)
      .catch(() => setView(null));
  }, []);

  if (!view) return null;

  const run = async (action: () => Promise<CourtScreeningView>) => {
    setBusy(true);
    setError(null);
    try {
      setView(await action());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('courtScreen.error'));
    } finally {
      setBusy(false);
    }
  };

  const due = view.responseDueAt ? new Date(view.responseDueAt).toLocaleDateString() : null;
  const checking = ['SEARCHING', 'MATCH_TO_CONFIRM', 'MATCH_CONFIRMED', 'RESPONSE_RECEIVED'].includes(view.status);

  return (
    <section className="space-y-2" data-testid="court-screening">
      <h2 className="px-1 text-caption font-semibold uppercase tracking-wide text-text-secondary">{t('courtScreen.heading')}</h2>
      <Card className="space-y-3">
        {view.status === 'CONSENT_REQUESTED' && (
          <>
            <p className="font-semibold text-text-primary">{t('courtScreen.consent.title')}</p>
            <p className="text-sm text-text-secondary">{t('courtScreen.consent.body')}</p>
            <button
              type="button"
              className="tap-target text-sm font-semibold text-primary"
              aria-expanded={fullText}
              onClick={() => setFullText(!fullText)}
            >
              {fullText ? t('courtScreen.consent.hide') : t('courtScreen.consent.read')}
            </button>
            {fullText && (
              <div className="max-h-72 space-y-3 overflow-y-auto rounded-card bg-background p-3 text-caption leading-relaxed text-text-secondary" lang="en">
                <p className="font-medium text-text-primary">{highlightPlaceholders(COURT_SCREEN_CONSENT.intro)}</p>
                {COURT_SCREEN_CONSENT.sections.map((s) => (
                  <div key={s.heading} className="space-y-1">
                    <p className="font-semibold text-text-primary">{s.heading}</p>
                    {s.paragraphs.map((p) => (
                      <p key={p}>{highlightPlaceholders(p)}</p>
                    ))}
                  </div>
                ))}
                <p>{COURT_SCREEN_CONSENT.effective}</p>
              </div>
            )}
            <Button fullWidth disabled={busy} onClick={() => run(() => verificationApi.acceptCourtScreening(view.consentVersion))} data-testid="court-screening-agree">
              {t('courtScreen.consent.agree')}
            </Button>
          </>
        )}

        {checking && (
          <>
            <p className="font-semibold text-text-primary">{t('courtScreen.checking.title')}</p>
            <p className="text-sm text-text-secondary">
              {view.status === 'RESPONSE_RECEIVED' ? t('courtScreen.checking.received') : t('courtScreen.checking.body')}
            </p>
          </>
        )}

        {view.status === 'AWAITING_RESPONSE' && (
          <>
            <p className="font-semibold text-text-primary">{t('courtScreen.moreInfo.title')}</p>
            <p className="text-sm text-text-secondary">{t('courtScreen.moreInfo.body')}</p>
            {due && <p className="text-sm font-medium text-text-primary">{t('courtScreen.moreInfo.due', { date: due })}</p>}
            <label className="block space-y-1 text-sm">
              <span className="font-medium text-text-primary">{t('courtScreen.moreInfo.label')}</span>
              <textarea
                className="w-full rounded-input border border-border bg-surface p-3 text-sm text-text-primary"
                rows={4}
                maxLength={1000}
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                data-testid="court-screening-answer"
              />
            </label>
            <Button fullWidth disabled={busy || !answer.trim()} onClick={() => run(() => verificationApi.respondToCourtScreening(answer.trim()))}>
              {t('courtScreen.moreInfo.send')}
            </Button>
            <p className="text-caption text-text-secondary">{t('courtScreen.moreInfo.pcc')}</p>
          </>
        )}

        {view.status === 'AWAITING_PCC' && (
          <>
            <p className="font-semibold text-text-primary">{t('courtScreen.pcc.title')}</p>
            <p className="text-sm text-text-secondary">{t('courtScreen.pcc.body')}</p>
            {due && <p className="text-sm font-medium text-text-primary">{t('courtScreen.moreInfo.due', { date: due })}</p>}
          </>
        )}

        {(view.status === 'APPROVED' || view.status === 'APPROVED_WITH_NOTE') && (
          <p className="text-sm text-text-secondary">{t('courtScreen.done')}</p>
        )}
        {view.status === 'REJECTED' && <p className="text-sm text-text-secondary">{t('courtScreen.notCleared')}</p>}
        {view.status === 'WITHDRAWN' && <p className="text-sm text-text-secondary">{t('courtScreen.withdrawn')}</p>}

        {error && (
          <p className="text-sm text-danger" role="alert">
            {error}
          </p>
        )}

        {/* Withdrawing is always possible while it is open, and honest about what it means. */}
        {!['CONSENT_REQUESTED', 'APPROVED', 'APPROVED_WITH_NOTE', 'REJECTED', 'WITHDRAWN'].includes(view.status) &&
          (confirmWithdraw ? (
            <div className="space-y-2 rounded-card bg-background p-3" role="group" aria-label={t('courtScreen.withdraw.label')}>
              <p className="text-sm text-text-primary">{t('courtScreen.withdraw.warning')}</p>
              <div className="flex gap-2">
                <Button variant="secondary" disabled={busy} onClick={() => setConfirmWithdraw(false)}>
                  {t('courtScreen.withdraw.keep')}
                </Button>
                <Button variant="danger" disabled={busy} onClick={() => run(() => verificationApi.withdrawCourtScreening())}>
                  {t('courtScreen.withdraw.confirm')}
                </Button>
              </div>
            </div>
          ) : (
            <button type="button" className="tap-target text-sm font-semibold text-text-secondary underline" onClick={() => setConfirmWithdraw(true)}>
              {t('courtScreen.withdraw.label')}
            </button>
          ))}
      </Card>
    </section>
  );
}

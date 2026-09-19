import { useEffect, useState } from 'react';
import { Download, Mail, Trash2 } from 'lucide-react';
import { Button } from './Button';
import { Card } from './Card';
import { ConfirmDialog } from './ConfirmDialog';
import { IconCircle } from './IconCircle';
import { ListRow } from './ListRow';
import { showToast } from '../lib/toast';
import { useTranslation } from 'react-i18next';

export interface PrivacyDataSectionProps {
  audience: 'customer' | 'driver';
  /** The Grievance Officer's email, or null when none is configured yet. */
  grievanceOfficerEmail: string | null;
  /** Downloads the person's data file. Resolve when saved; reject with a message to show. */
  onDownload: () => Promise<void>;
  /** Deletes the account. Resolve when the server has done it; reject with a message to show. */
  onDelete: () => Promise<void>;
  /** Called after a completed deletion - the app signs out and leaves. */
  onDeleted: () => void;
}

const CONFIRM_WORD = 'DELETE';

/**
 * Profile's Privacy & Data section: download my data, delete my account, and
 * who to write to about either.
 * <p>
 * Deleting is two steps, on purpose. First the same confirmation dialog every
 * other consequential action uses, carrying the plain explanation of what
 * happens; then typing DELETE. A single tap cannot do it, and nobody reaches
 * the second step without having been shown the first.
 * <p>
 * The explanation is honest about what is kept. "Everything will be erased"
 * would be false - completed trips and payments stay for the period the law
 * requires - and a person deciding this deserves to know that before, not
 * after.
 */
export function PrivacyDataSection({
  audience,
  grievanceOfficerEmail,
  onDownload,
  onDelete,
  onDeleted,
}: PrivacyDataSectionProps) {
  const { t } = useTranslation('ds');
  const [downloading, setDownloading] = useState(false);
  const [step, setStep] = useState<'idle' | 'explain' | 'type'>('idle');
  const [typed, setTyped] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (step !== 'type') {
      setTyped('');
      setError(null);
    }
  }, [step]);

  async function download() {
    if (downloading) return;
    setDownloading(true);
    try {
      // No success toast: the browser's own download UI already says it
      // arrived, and a toast here sat over the delete dialog's buttons.
      await onDownload();
    } catch (err) {
      showToast(err instanceof Error ? err.message : t('privacy.downloadError'));
    } finally {
      setDownloading(false);
    }
  }

  async function confirmDelete() {
    if (typed !== CONFIRM_WORD || deleting) return;
    setDeleting(true);
    setError(null);
    try {
      await onDelete();
      onDeleted();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('privacy.deleteError'));
    } finally {
      setDeleting(false);
    }
  }

  const explanation = [
    t('privacy.explain.closed'),
    audience === 'driver' ? t('privacy.explain.removedDriver') : t('privacy.explain.removedRider'),
    t('privacy.explain.messages'),
    t('privacy.explain.kept'),
    t('privacy.explain.final'),
  ].join('\n\n');

  return (
    <section>
      <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('privacy.heading')}</h2>
      <Card className="divide-y divide-border p-0">
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<Download />} />}
          label={downloading ? t('privacy.preparing') : t('privacy.download')}
          sublabel={t('privacy.downloadSub')}
          onClick={() => void download()}
        />
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<Mail />} />}
          label={t('privacy.grievance')}
          sublabel={grievanceOfficerEmail ?? t('privacy.grievancePending')}
          onClick={grievanceOfficerEmail ? () => { window.location.href = `mailto:${grievanceOfficerEmail}`; } : undefined}
          chevron={Boolean(grievanceOfficerEmail)}
        />
        <ListRow
          icon={<IconCircle color="red" tone="soft" size="sm" icon={<Trash2 />} />}
          label={t('privacy.delete')}
          sublabel={t('privacy.deleteSub')}
          onClick={() => setStep('explain')}
        />
      </Card>

      <ConfirmDialog
        open={step === 'explain'}
        title={t('privacy.deleteTitle')}
        message={explanation}
        confirmLabel={t('common.continue')}
        destructive
        onConfirm={() => setStep('type')}
        onCancel={() => setStep('idle')}
      />

      {step === 'type' && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-text-primary/40 px-6"
          role="dialog"
          aria-modal="true"
          aria-label={t('privacy.confirmAria')}
          onClick={() => !deleting && setStep('idle')}
        >
          <div className="w-full max-w-xs rounded-card bg-surface p-5 shadow-card" onClick={(e) => e.stopPropagation()}>
            <p className="font-heading text-lg font-semibold text-text-primary">{t('privacy.typeToConfirm')}</p>
            <p className="mt-2 text-sm text-text-secondary">
              {t('privacy.permanent')}
            </p>
            <input
              aria-label={t('privacy.typeToConfirm')}
              name="confirmDelete"
              autoFocus
              autoCapitalize="characters"
              autoComplete="off"
              className="mt-4 h-11 w-full rounded-input border border-border bg-surface px-3 text-sm tracking-widest text-text-primary outline-none focus:border-danger"
              value={typed}
              disabled={deleting}
              onChange={(e) => setTyped(e.target.value)}
              placeholder="DELETE"
            />
            {error && <p className="mt-2 text-sm text-danger">{error}</p>}
            <div className="mt-5 flex gap-3">
              <Button variant="secondary" fullWidth disabled={deleting} onClick={() => setStep('idle')}>
                {t('common.cancel')}
              </Button>
              <Button variant="danger" fullWidth disabled={typed !== CONFIRM_WORD || deleting} onClick={() => void confirmDelete()}>
                {deleting ? t('privacy.deleting') : t('privacy.deleteButton')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

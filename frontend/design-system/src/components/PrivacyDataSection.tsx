import { useEffect, useState } from 'react';
import { Download, Mail, Trash2 } from 'lucide-react';
import { Button } from './Button';
import { Card } from './Card';
import { ConfirmDialog } from './ConfirmDialog';
import { IconCircle } from './IconCircle';
import { ListRow } from './ListRow';
import { showToast } from '../lib/toast';

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
      showToast(err instanceof Error ? err.message : 'Could not download your data. Try again.');
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
      setError(err instanceof Error ? err.message : 'Could not delete your account. Try again.');
    } finally {
      setDeleting(false);
    }
  }

  const explanation = [
    'Your account is closed straight away: you are signed out everywhere and cannot sign back in to it.',
    audience === 'driver'
      ? 'You go offline permanently and stop receiving trips. Your name, vehicle registration, profile photo and ID documents are removed - the photo and documents are deleted from storage, not just hidden.'
      : 'Your name, saved addresses, emergency contacts and ID documents are removed - the documents are deleted from storage, not just hidden.',
    'Your messages are deleted, except on a trip with an open support dispute, where they are kept as "[deleted]".',
    'Completed trips and payments are kept for the period Indian tax and dispute rules require, but with your name shown as "Deleted User" and your phone number removed.',
    'This cannot be undone. Signing in with the same number later starts a new, empty account.',
  ].join('\n\n');

  return (
    <section>
      <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Privacy &amp; Data</h2>
      <Card className="divide-y divide-border p-0">
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<Download />} />}
          label={downloading ? 'Preparing your data...' : 'Download my data'}
          sublabel="A copy of everything SheOut holds about you"
          onClick={() => void download()}
        />
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<Mail />} />}
          label="Grievance Officer"
          sublabel={grievanceOfficerEmail ?? 'Contact details are being set up - use Help & Support meanwhile'}
          onClick={grievanceOfficerEmail ? () => { window.location.href = `mailto:${grievanceOfficerEmail}`; } : undefined}
          chevron={Boolean(grievanceOfficerEmail)}
        />
        <ListRow
          icon={<IconCircle color="red" tone="soft" size="sm" icon={<Trash2 />} />}
          label="Delete my account"
          sublabel="Close your account and remove your personal details"
          onClick={() => setStep('explain')}
        />
      </Card>

      <ConfirmDialog
        open={step === 'explain'}
        title="Delete your account?"
        message={explanation}
        confirmLabel="Continue"
        destructive
        onConfirm={() => setStep('type')}
        onCancel={() => setStep('idle')}
      />

      {step === 'type' && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-text-primary/40 px-6"
          role="dialog"
          aria-modal="true"
          aria-label="Confirm account deletion"
          onClick={() => !deleting && setStep('idle')}
        >
          <div className="w-full max-w-xs rounded-card bg-surface p-5 shadow-card" onClick={(e) => e.stopPropagation()}>
            <p className="font-heading text-lg font-semibold text-text-primary">Type DELETE to confirm</p>
            <p className="mt-2 text-sm text-text-secondary">
              This permanently closes your account. There is no way to undo it.
            </p>
            <input
              aria-label="Type DELETE to confirm"
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
                Cancel
              </Button>
              <Button variant="danger" fullWidth disabled={typed !== CONFIRM_WORD || deleting} onClick={() => void confirmDelete()}>
                {deleting ? 'Deleting...' : 'Delete'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

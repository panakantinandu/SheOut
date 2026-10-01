import { Clock, FileImage, XCircle } from 'lucide-react';
import { useRef, useState } from 'react';
import { Button, Card, IconCircle, shrinkPhoto, useTranslation, vehicleLabel } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { DriverProfileSummary, PendingProfileChange } from '../api/types';

/**
 * A change to what riders identify her by, waiting for SheOut's team - or
 * one just turned down, with the reason. Until it is approved riders keep
 * seeing the details that were checked, and this card says so, so she is
 * never left wondering why her profile "did not save".
 * <p>
 * A new vehicle cannot be approved without a photo of its registration
 * certificate, so when one is missing that is the one thing asked of her.
 */
export function PendingChangeCard({ change, onUpdated }: { change: PendingProfileChange; onUpdated: (p: DriverProfileSummary) => void }) {
  const { t } = useTranslation();
  const rcInput = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(action: () => Promise<DriverProfileSummary>) {
    setBusy(true);
    setError(null);
    try {
      onUpdated(await action());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('profile.saveError'));
    } finally {
      setBusy(false);
    }
  }

  if (change.status === 'REJECTED') {
    return (
      <Card tone="danger" className="flex items-start gap-3" data-testid="profile-change-rejected">
        <IconCircle tone="soft" color="red" icon={<XCircle />} />
        <div className="min-w-0 flex-1">
          <p className="font-heading text-card-title text-text-primary">{t('profile.change.rejectedTitle')}</p>
          {change.decisionNote && <p className="mt-1 text-sm text-text-primary">“{change.decisionNote}”</p>}
          <p className="mt-1 text-xs text-text-secondary">{t('profile.change.rejectedBody')}</p>
        </div>
      </Card>
    );
  }

  const items: string[] = [];
  if (change.name) items.push(t('profile.change.name', { value: change.name }));
  if (change.dateOfBirth) items.push(t('profile.change.dob', { value: change.dateOfBirth }));
  if (change.vehicleType || change.vehicleRegistrationNumber) {
    items.push(t('profile.change.vehicle', { value: `${vehicleLabel(change.vehicleType)} ${change.vehicleRegistrationNumber ?? ''}`.trim() }));
  }
  if (change.photoChanged) items.push(t('profile.change.photo'));
  const needsRc = change.rcDocumentRequired && !change.rcDocumentAttached;

  return (
    <Card tone="warning" className="space-y-3" data-testid="profile-change-pending">
      <div className="flex items-start gap-3">
        <IconCircle tone="soft" color="orange" icon={<Clock />} />
        <div className="min-w-0 flex-1">
          <p className="font-heading text-card-title text-text-primary">{t('profile.change.pendingTitle')}</p>
          <ul className="mt-1 list-disc pl-4 text-sm text-text-primary">
            {items.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <p className="mt-1 text-xs text-text-secondary">{t('profile.change.pendingBody')}</p>
        </div>
      </div>
      {change.photoUrl && <img src={change.photoUrl} alt="" className="h-20 w-20 rounded-full object-cover" />}
      {needsRc && <p className="text-sm font-medium text-text-primary">{t('profile.change.rcNeeded')}</p>}
      <input
        ref={rcInput}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={async (e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (file) await run(async () => usersApi.attachVehicleRc(await shrinkPhoto(file)));
        }}
      />
      {change.rcDocumentRequired && (
        <Button
          fullWidth
          variant={needsRc ? 'primary' : 'secondary'}
          icon={<FileImage className="h-4 w-4" />}
          disabled={busy}
          onClick={() => rcInput.current?.click()}
          data-testid="profile-change-rc"
        >
          {change.rcDocumentAttached ? t('profile.change.rcReplace') : t('profile.change.rcAdd')}
        </Button>
      )}
      <Button fullWidth variant="secondary" disabled={busy} onClick={() => run(() => usersApi.withdrawProfileChange())} data-testid="profile-change-withdraw">
        {t('profile.change.withdraw')}
      </Button>
      {error && <p className="text-sm text-danger">{error}</p>}
    </Card>
  );
}

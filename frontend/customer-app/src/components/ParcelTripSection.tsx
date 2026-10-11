import { Package } from 'lucide-react';
import { Card, IconCircle, useTranslation } from '@sheout/design-system';

/**
 * A parcel's own details, in their own card under the trip card, so the
 * card a ride and a parcel share stays the same shape for both: what is in
 * it, in the sender's words, and what to do at this step.
 * <p>
 * The receiver's name and number are not here because the booking does not
 * have them - see docs/parcel-dispatch-audit.md (A.2).
 */
export function ParcelTripSection({ note, onTrip }: { note: string | null | undefined; onTrip: boolean }) {
  const { t } = useTranslation();
  return (
    <Card className="space-y-3" data-testid="parcel-section">
      <div className="flex items-center gap-3">
        <IconCircle size="md" tone="soft" color="orange" icon={<Package />} />
        <p className="font-heading text-card-title text-text-primary">{t('tracking.parcel.title')}</p>
      </div>
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-text-secondary">{t('tracking.parcel.contents')}</p>
        <p className="line-clamp-3 text-sm text-text-primary" data-testid="parcel-section-note">
          {note?.trim() ? note : t('tracking.parcel.noNote')}
        </p>
      </div>
      <p className="rounded-input bg-background px-3 py-2 text-sm text-text-secondary">
        {onTrip ? t('tracking.parcel.onTheWay') : t('tracking.parcel.handover')}
      </p>
    </Card>
  );
}

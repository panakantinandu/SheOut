import { Clock, MoonStar, PauseCircle } from 'lucide-react';
import { Card, IconCircle, useAppLanguage, useTranslation } from '@sheout/design-system';
import type { ServiceStatus } from '../api/client';
import { formatMoment, formatWindowTime } from '../lib/useServiceStatus';

/** How soon before closing she is told bookings are about to stop. */
const CLOSING_SOON_MS = 45 * 60_000;

/**
 * Says when SheOut is not taking bookings, and why, before she plans a trip
 * rather than after: outside the operating hours ("for everyone's safety,
 * 6:00 AM - 10:00 PM"), or paused by operations with the reason they gave.
 * Open, it says nothing - except in the last stretch before closing, when a
 * quiet line says when bookings stop tonight.
 * <p>
 * Trips already booked are never affected, and the copy says so: a rider
 * mid-trip at 10 PM must not read this as her ride being cancelled.
 */
export function ServiceHoursNotice({ status, compact = false }: { status: ServiceStatus | null; compact?: boolean }) {
  const { t } = useTranslation();
  const lang = useAppLanguage();
  if (!status) return null;

  const opens = formatWindowTime(status.opensAt, lang);
  const closes = formatWindowTime(status.closesAtLocal, lang);

  if (status.open) {
    if (!status.closesAt) return null;
    const left = new Date(status.closesAt).getTime() - Date.now();
    if (left > CLOSING_SOON_MS || left <= 0) return null;
    return (
      <p className="flex items-center gap-2 rounded-input bg-accent-orange-tint px-3 py-2 text-xs font-medium text-text-primary" data-testid="service-closing-soon">
        <Clock className="h-4 w-4 shrink-0 text-accent-orange-strong" aria-hidden="true" />
        {t('serviceHours.closingSoon', { time: formatMoment(status.closesAt, lang) })}
      </p>
    );
  }

  const paused = status.closedReason === 'PAUSED';
  const reopens = status.reopensAt ? formatMoment(status.reopensAt, lang) : null;
  return (
    <Card tone="warning" className={`flex items-start gap-3 ${compact ? 'p-4' : ''}`} data-testid="service-closed">
      <IconCircle color="orange" tone="soft" icon={paused ? <PauseCircle /> : <MoonStar />} />
      <div className="min-w-0 flex-1">
        <p className="font-heading text-card-title text-text-primary">
          {paused ? t('serviceHours.pausedTitle') : t('serviceHours.closedTitle')}
        </p>
        <p className="mt-1 text-sm text-text-secondary">
          {paused
            ? status.pauseReason || t('serviceHours.pausedBody')
            : t('serviceHours.closedBody', { opens, closes })}
        </p>
        <p className="mt-1 text-sm font-medium text-text-primary">
          {reopens ? t('serviceHours.bookAgainAt', { time: reopens }) : t('serviceHours.backSoon')}
        </p>
        {!compact && <p className="mt-1 text-xs text-text-secondary">{t('serviceHours.tripsUnaffected')}</p>}
      </div>
    </Card>
  );
}

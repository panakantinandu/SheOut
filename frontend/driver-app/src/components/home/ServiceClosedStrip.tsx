import { MoonStar, PauseCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Card, IconCircle, useAppLanguage, useTranslation } from '@sheout/design-system';
import { serviceStatusApi, type ServiceStatus } from '../../api/client';

const REFRESH_MS = 60_000;
const INDIA = 'Asia/Kolkata';

/**
 * Tells her when SheOut is not taking bookings - outside the operating
 * hours, or paused by operations - so she is not online at 11 PM waiting
 * for offers that cannot come. Going online is not blocked: a partner
 * finishing a trip, or heading home, still needs SOS and her location
 * shared, and the trip she is on is never affected.
 * <p>
 * Says nothing when open or when the status cannot be read; an unknown is
 * never shown as closed.
 */
export function ServiceClosedStrip() {
  const { t } = useTranslation();
  const lang = useAppLanguage();
  const [status, setStatus] = useState<ServiceStatus | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = () => {
      if (document.hidden) return;
      serviceStatusApi
        .get()
        .then((s) => !cancelled && setStatus(s))
        .catch(() => undefined);
    };
    load();
    const timer = window.setInterval(load, REFRESH_MS);
    document.addEventListener('visibilitychange', load);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', load);
    };
  }, []);

  if (!status || status.open) return null;
  const paused = status.closedReason === 'PAUSED';
  const reopens = status.reopensAt
    ? new Date(status.reopensAt).toLocaleString(lang, { hour: 'numeric', minute: '2-digit', weekday: 'short', timeZone: INDIA })
    : null;

  return (
    <Card tone="warning" className="flex items-start gap-3" data-testid="service-closed">
      <IconCircle color="orange" tone="soft" icon={paused ? <PauseCircle /> : <MoonStar />} />
      <div className="min-w-0 flex-1">
        <p className="font-heading text-card-title text-text-primary">{t('serviceHours.noNewTrips')}</p>
        <p className="mt-1 text-sm text-text-secondary">
          {paused ? status.pauseReason || t('serviceHours.paused') : t('serviceHours.outsideHours')}
        </p>
        <p className="mt-1 text-sm font-medium text-text-primary">
          {reopens ? t('serviceHours.reopens', { time: reopens }) : t('serviceHours.backSoon')}
        </p>
        <p className="mt-1 text-xs text-text-secondary">{t('serviceHours.currentTripSafe')}</p>
      </div>
    </Card>
  );
}

import { Wifi, WifiOff } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

/** How long "Back online" stays up once the connection returns. */
const BACK_ONLINE_MS = 2500;

/**
 * A small, calm pill at the top of every screen while the phone has no connection,
 * and a short "Back online" when it returns - so a screen that cannot load
 * says why, instead of looking empty or broken. It stays clear of the
 * header's corner buttons (back, menu, bell) and never takes a tap.
 */
export function OfflineBanner() {
  const { t } = useTranslation('ds');
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine));
  const [backOnline, setBackOnline] = useState(false);

  useEffect(() => {
    let timer: number | undefined;
    const goOffline = () => {
      window.clearTimeout(timer);
      setBackOnline(false);
      setOnline(false);
    };
    const goOnline = () => {
      setOnline(true);
      setBackOnline(true);
      timer = window.setTimeout(() => setBackOnline(false), BACK_ONLINE_MS);
    };
    window.addEventListener('offline', goOffline);
    window.addEventListener('online', goOnline);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('offline', goOffline);
      window.removeEventListener('online', goOnline);
    };
  }, []);

  if (online && !backOnline) return null;
  return (
    <div
      className="pointer-events-none fixed inset-x-0 top-0 z-[70] mx-auto flex max-w-md justify-center px-[3.75rem] pt-[max(0.75rem,env(safe-area-inset-top))]"
      role="status"
      aria-live="polite"
      data-testid="offline-banner"
    >
      <p
        className={`inline-flex min-w-0 items-center gap-2 rounded-full px-3.5 py-1.5 text-sm font-medium shadow-float ${
          online ? 'bg-accent-green-tint text-accent-green-strong' : 'bg-accent-orange-tint text-accent-orange-strong'
        }`}
      >
        {online ? <Wifi className="h-4 w-4 shrink-0" aria-hidden="true" /> : <WifiOff className="h-4 w-4 shrink-0" aria-hidden="true" />}
        <span className="truncate">{online ? t('offline.back') : t('offline.title')}</span>
      </p>
    </div>
  );
}

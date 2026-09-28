import { useCallback, useEffect, useState } from 'react';
import { LiveMap, reportBreadcrumb, reportMeasurement, useTranslation } from '@sheout/design-system';
import { usePageVisible } from '../../lib/driverStatus';

/**
 * Off unless the build sets VITE_DRIVER_HOME_MAP_ENABLED=true: on in
 * staging, off in production until the map-load numbers say the cost is
 * worth it. Every dynamic map Google creates is a billed load.
 */
export const DRIVER_HOME_MAP_ENABLED = import.meta.env.VITE_DRIVER_HOME_MAP_ENABLED === 'true';

const LOADS_KEY = 'sheout_home_map_loads';
/** Re-centre only once she has moved this far: the marker moves, the map mostly does not. */
const RECENTRE_METRES = 400;

/**
 * One report per session, when the page goes away - registered at page
 * level on the first load, so it still fires if she went offline (and the
 * card unmounted) before closing the app.
 */
let sessionReportRegistered = false;
function reportAtSessionEnd() {
  if (sessionReportRegistered) return;
  sessionReportRegistered = true;
  window.addEventListener('pagehide', () => {
    let loads = 0;
    try {
      loads = Number(sessionStorage.getItem(LOADS_KEY) ?? '0');
    } catch {
      return;
    }
    if (loads) reportMeasurement('driver-home-map-loads', { loads });
  }, { once: true });
}

function metres(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const k = 111195;
  return Math.hypot((a.lat - b.lat) * k, (a.lng - b.lng) * k * Math.cos((a.lat * Math.PI) / 180));
}

/**
 * Where she is, while she waits for a request: a small, glance-only map
 * (no pan or zoom, the page scrolls through it) with her bike marker.
 * <p>
 * Mounted only while online, and only when the flag is on. Only the marker
 * follows her; the view re-centres just when she has moved well away. While
 * the app is hidden the position is frozen, so nothing redraws off screen.
 * <p>
 * COST, MEASURED. Each map Google creates is counted for this session
 * (sessionStorage) and, when the session ends, sent to Sentry as
 * "driver-home-map-loads" if Sentry is configured - the number to weigh
 * before turning the flag on in production. Google Cloud Console's
 * per-key map-load report is the billing figure itself.
 */
export function HomeMapCard({ position }: { position: { lat: number; lng: number; heading?: number | null } | null }) {
  const { t } = useTranslation();
  const visible = usePageVisible();
  const [shown, setShown] = useState(position);
  const [centre, setCentre] = useState(position);

  // Frozen while hidden.
  useEffect(() => {
    if (!visible || !position) return;
    setShown(position);
    setCentre((c) => (!c || metres(c, position) > RECENTRE_METRES ? position : c));
  }, [visible, position?.lat, position?.lng, position?.heading]);

  const onMapLoad = useCallback(() => {
    let loads = 1;
    try {
      loads = Number(sessionStorage.getItem(LOADS_KEY) ?? '0') + 1;
      sessionStorage.setItem(LOADS_KEY, String(loads));
    } catch {
      // Private mode: counted for this page only.
    }
    reportBreadcrumb('maps', `driver home map load #${loads}`);
    reportAtSessionEnd();
  }, []);

  if (!shown || !centre) return null;
  return (
    <div className="space-y-1" data-testid="home-map-card">
      <LiveMap
        markers={[{ key: 'me', lat: shown.lat, lng: shown.lng, label: t('home.you'), kind: 'driver', heading: shown.heading }]}
        autoFit={false}
        center={centre}
        zoom={15}
        interactive={false}
        onMapLoad={onMapLoad}
        className="h-40"
      />
      <p className="text-xs text-text-secondary">{t('home.positionNote')}</p>
    </div>
  );
}

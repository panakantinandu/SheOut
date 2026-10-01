import { useEffect, useState } from 'react';
import { serviceStatusApi, type ServiceStatus } from '../api/client';

/** Often enough that the app notices closing time or a pause while it sits open. */
const REFRESH_MS = 60_000;

let current: ServiceStatus | null = null;
let inFlight: Promise<void> | null = null;
const listeners = new Set<(s: ServiceStatus | null) => void>();

function fetchStatus(): Promise<void> {
  if (!inFlight) {
    inFlight = serviceStatusApi
      .get()
      .then((s) => {
        current = s;
        listeners.forEach((l) => l(s));
      })
      .catch(() => {
        // Offline, or an older backend. Say nothing rather than "closed":
        // the server's own check on booking is the real gate either way.
      })
      .finally(() => {
        inFlight = null;
      });
  }
  return inFlight;
}

/** Ask again now - after the server refused a booking as SERVICE_CLOSED, say. */
export function refreshServiceStatus(): Promise<void> {
  return fetchStatus();
}

/**
 * Whether SheOut is taking bookings right now, shared by every screen that
 * shows it. Null until the first answer, and stays null if the server cannot
 * be asked - an unknown is never shown as closed.
 * <p>
 * Refreshed every minute while a screen using it is open, when the app comes
 * back to the foreground, and the moment the server's "closes at" or
 * "reopens at" passes, so the notice changes at 10 PM rather than up to a
 * minute after.
 */
export function useServiceStatus(): ServiceStatus | null {
  const [status, setStatus] = useState<ServiceStatus | null>(current);

  useEffect(() => {
    listeners.add(setStatus);
    void fetchStatus();
    const timer = window.setInterval(() => {
      if (!document.hidden) void fetchStatus();
    }, REFRESH_MS);
    const onVisible = () => {
      if (!document.hidden) void fetchStatus();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      listeners.delete(setStatus);
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, []);

  // The next change of state, to the second.
  const nextChange = status ? (status.open ? status.closesAt : status.reopensAt) : null;
  useEffect(() => {
    if (!nextChange) return;
    const wait = new Date(nextChange).getTime() - Date.now() + 1000;
    if (wait <= 0 || wait > 12 * 3600_000) return;
    const timer = window.setTimeout(() => void fetchStatus(), wait);
    return () => window.clearTimeout(timer);
  }, [nextChange]);

  return status;
}

const INDIA = 'Asia/Kolkata';

/** "6:00 AM" for a time of day in India, in her language's own clock style. */
export function formatWindowTime(localTime: string, lang: string): string {
  const [h, m] = localTime.split(':').map(Number);
  // That time of day in India, on an arbitrary date, formatted in India's zone.
  const instant = new Date(Date.UTC(2000, 0, 1, h, m) - 330 * 60_000);
  return instant.toLocaleTimeString(lang, { hour: 'numeric', minute: '2-digit', timeZone: INDIA });
}

/** "6:00 AM", or "6:00 AM, Thu" when it is not today in India. */
export function formatMoment(iso: string, lang: string): string {
  const at = new Date(iso);
  const time = at.toLocaleTimeString(lang, { hour: 'numeric', minute: '2-digit', timeZone: INDIA });
  const day = (d: Date) => d.toLocaleDateString('en-CA', { timeZone: INDIA });
  if (day(at) === day(new Date())) return time;
  return `${time}, ${at.toLocaleDateString(lang, { weekday: 'short', timeZone: INDIA })}`;
}

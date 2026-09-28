import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Overlay, SafetyText, useSafetyString } from '@sheout/design-system';
import { usersApi } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { activeTripId, motionNeedsPermission, requestMotionPermission, useDiscreetSosListener } from '../lib/discreetSos';
import { cacheContacts, cachePosition, flushSosQueue, pendingSos, type TriggerSource } from '../lib/sosDelivery';

const COUNTDOWN_SECONDS = 3;

interface DiscreetSos {
  /** Starts the "SOS in 3" countdown, as a gesture or the phone's shortcut does. */
  startCountdown: (source: TriggerSource) => void;
}

const DiscreetSosContext = createContext<DiscreetSos>({ startCountdown: () => undefined });

export function useDiscreetSos(): DiscreetSos {
  return useContext(DiscreetSosContext);
}

/**
 * Around the whole signed-in app:
 * <ul>
 *   <li>the discreet-SOS gestures, and the 3-second countdown they start -
 *       with a large Cancel, so an accidental shake alerts nobody. Doing the
 *       gesture again during the countdown sends at once: that is someone
 *       who means it and may not be able to look at the screen. If nothing
 *       happens, the SOS goes out exactly as the button sends it;</li>
 *   <li>the offline SOS queue, sent the moment the connection returns;</li>
 *   <li>her contacts and last position, kept on the phone so the SMS
 *       fallback works with no connection at all.</li>
 * </ul>
 */
export function DiscreetSosGuard({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const s = useSafetyString();
  const [countdown, setCountdown] = useState<{ source: TriggerSource; left: number } | null>(null);
  const [needsTap, setNeedsTap] = useState(false);
  const countdownRef = useRef(countdown);
  countdownRef.current = countdown;

  const fire = useCallback(
    (source: TriggerSource) => {
      setCountdown(null);
      navigate('/sos', { state: { autoSend: { source }, bookingId: activeTripId() ?? undefined } });
    },
    [navigate]
  );

  const startCountdown = useCallback(
    (source: TriggerSource) => {
      if (countdownRef.current) {
        // The same gesture again, mid-countdown: she means it.
        fire(countdownRef.current.source);
        return;
      }
      setCountdown({ source, left: COUNTDOWN_SECONDS });
    },
    [fire]
  );

  const { armed, listening } = useDiscreetSosListener((gesture) => startCountdown(gesture));

  // The countdown itself, with a buzz each second she can feel without looking.
  useEffect(() => {
    if (!countdown) return;
    try {
      navigator.vibrate?.(countdown.left > 0 ? 200 : 0);
    } catch {
      // No vibration here.
    }
    if (countdown.left <= 0) {
      fire(countdown.source);
      return;
    }
    const timer = window.setTimeout(() => setCountdown((c) => (c ? { ...c, left: c.left - 1 } : c)), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown, fire]);

  // On an iPhone whose motion permission has lapsed, readings never arrive:
  // offer one small tap to turn it back on, during the trip, never mid-emergency.
  useEffect(() => {
    if (!armed || listening || !motionNeedsPermission()) {
      setNeedsTap(false);
      return;
    }
    const timer = window.setTimeout(() => setNeedsTap(true), 3000);
    return () => window.clearTimeout(timer);
  }, [armed, listening]);

  // The offline queue, her contacts and her last position.
  useEffect(() => {
    if (!isAuthenticated) return;
    const flush = () => {
      if (pendingSos().length > 0) void flushSosQueue();
    };
    flush();
    window.addEventListener('online', flush);
    const timer = window.setInterval(flush, 30000);
    Promise.all([usersApi.getMyEmergencyContacts(), usersApi.getMyProfile().catch(() => null)])
      .then(([contacts, profile]) =>
        cacheContacts({
          names: contacts.map((c) => c.name),
          numbers: contacts.map((c) => c.phoneNumber),
          myName: profile?.name?.split(' ')[0] || undefined,
        })
      )
      .catch(() => undefined);
    return () => {
      window.removeEventListener('online', flush);
      window.clearInterval(timer);
    };
  }, [isAuthenticated]);

  useEffect(() => {
    if (!armed || !navigator.geolocation) return;
    const refresh = () =>
      navigator.geolocation.getCurrentPosition(
        (p) => cachePosition(p.coords.latitude, p.coords.longitude),
        () => undefined,
        { timeout: 10000, maximumAge: 60000 }
      );
    refresh();
    const timer = window.setInterval(refresh, 60000);
    return () => window.clearInterval(timer);
  }, [armed]);

  return (
    <DiscreetSosContext.Provider value={{ startCountdown }}>
      {children}
      {needsTap && !countdown && (
        <button
          type="button"
          onClick={async () => {
            if ((await requestMotionPermission()) === 'granted') setNeedsTap(false);
          }}
          className="fixed inset-x-4 bottom-24 z-40 mx-auto max-w-md rounded-full bg-text-primary px-4 py-3 text-sm font-semibold text-background shadow-float"
          data-testid="discreet-sos-permission"
        >
          <SafetyText k="discreet.permissionChip" />
        </button>
      )}
      <Overlay open={countdown !== null} label={s('discreet.countdownTitle')} align="centre" className="px-4">
        <div className="w-full rounded-card bg-surface p-6 text-center shadow-overlay" data-testid="sos-countdown" role="alertdialog">
          <p className="font-heading text-section text-text-primary"><SafetyText k="discreet.countdownTitle" /></p>
          <p className="my-4 font-heading text-[5rem] leading-none text-danger" aria-live="assertive" data-testid="sos-countdown-seconds">
            {countdown?.left ?? 0}
          </p>
          <p className="text-sm text-text-secondary"><SafetyText k="discreet.countdownBody" /></p>
          <button
            type="button"
            autoFocus
            onClick={() => setCountdown(null)}
            className="mt-5 w-full rounded-full bg-text-primary py-5 font-heading text-title text-background"
            data-testid="sos-countdown-cancel"
          >
            <SafetyText k="discreet.cancel" />
          </button>
          <button
            type="button"
            onClick={() => countdown && fire(countdown.source)}
            className="mt-3 w-full rounded-full border-2 border-danger py-3 font-semibold text-danger"
            data-testid="sos-countdown-send-now"
          >
            <SafetyText k="discreet.sendNow" />
          </button>
        </div>
      </Overlay>
    </DiscreetSosContext.Provider>
  );
}

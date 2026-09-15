import { useCallback, useEffect, useState } from 'react';

/** What GET /api/v1/notifications/push-config returns. Every value is public. */
export interface PushConfig {
  enabled: boolean;
  firebase: Record<string, string> | null;
  vapidKey: string | null;
}

/** The three calls push needs, supplied by each app's own API client. */
export interface PushApi {
  getConfig(): Promise<PushConfig>;
  registerDevice(token: string): Promise<void>;
  unregisterDevice(token: string): Promise<void>;
}

/**
 * - unsupported: this browser cannot receive web push at all (for example
 *   iOS Safari outside an installed home-screen app)
 * - unavailable: push is not configured on the server
 * - blocked: she said no, and only the browser's site settings can undo it
 * - off: not asked yet, or asked and dismissed
 * - on: this device is registered
 */
export type PushStatus = 'checking' | 'unsupported' | 'unavailable' | 'blocked' | 'off' | 'on';

const FIREBASE_APP_NAME = 'sheout-push';
const DISMISS_DAYS = 7;

export function pushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.isSecureContext &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/**
 * The service worker the app already runs. Push rides on it - a second
 * worker just for Firebase would fight the PWA worker for the same scope.
 * <p>
 * On an app's very first open the worker is still installing - downloading
 * the whole app to cache - when this runs. That took ~20 seconds on the live
 * site from a desktop connection, and longer on a phone. An earlier version
 * gave up after 10 seconds, so a brand-new user's first session silently got
 * no push prompt. Once a registration exists, this now waits for it however
 * long it takes; it only gives up when none appears at all (the dev server
 * registers none).
 */
async function appServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  for (let attempt = 0; attempt < 20; attempt++) {
    // registerAppUpdates registers the worker as the app starts; give that a moment.
    if (await navigator.serviceWorker.getRegistration()) {
      return navigator.serviceWorker.ready;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return null;
}

/**
 * Gets this device's FCM token and registers it against the signed-in
 * account. With askPermission false it never shows a browser prompt: it only
 * refreshes a device that already has permission, which is what runs on
 * every app start - FCM can rotate a token at any time.
 */
export async function enablePush(api: PushApi, storageKey: string, askPermission: boolean): Promise<PushStatus> {
  if (!pushSupported()) return 'unsupported';
  const config = await api.getConfig().catch(() => null);
  if (!config?.enabled || !config.firebase || !config.vapidKey) return 'unavailable';

  if (Notification.permission === 'denied') return 'blocked';
  if (Notification.permission === 'default') {
    if (!askPermission) return 'off';
    const answer = await Notification.requestPermission();
    if (answer === 'denied') return 'blocked';
    if (answer !== 'granted') return 'off';
  }

  const registration = await appServiceWorker();
  if (!registration) return 'unsupported';

  const [{ initializeApp, getApps }, messaging] = await Promise.all([import('firebase/app'), import('firebase/messaging')]);
  if (!(await messaging.isSupported())) return 'unsupported';
  const app = getApps().find((a) => a.name === FIREBASE_APP_NAME) ?? initializeApp(config.firebase, FIREBASE_APP_NAME);
  const token = await messaging.getToken(messaging.getMessaging(app), {
    vapidKey: config.vapidKey,
    serviceWorkerRegistration: registration,
  });
  if (!token) return 'off';

  await api.registerDevice(token);
  try {
    localStorage.setItem(storageKey, token);
  } catch {
    // Storage disabled: the device is still registered, it just cannot be unregistered at sign-out.
  }
  return 'on';
}

/**
 * Sign-out on this device: stop pushes to the account that is leaving. Must
 * run while the session is still valid, so before the token is cleared.
 */
export async function disablePushOnSignOut(api: PushApi, storageKey: string): Promise<void> {
  let token: string | null = null;
  try {
    token = localStorage.getItem(storageKey);
    localStorage.removeItem(storageKey);
  } catch {
    return;
  }
  if (token) {
    await api.unregisterDevice(token).catch(() => undefined);
  }
}

/**
 * Push for a signed-in screen: silently refreshes a device that already has
 * permission, and says whether to offer the "turn on notifications" prompt.
 * The prompt is a button she taps, not a prompt fired on load - iOS only
 * allows asking from a tap, and a surprise browser dialog is the one people
 * reflexively block.
 */
export function usePushNotifications(api: PushApi, storageKey: string, enabled: boolean) {
  const [status, setStatus] = useState<PushStatus>('checking');
  const [busy, setBusy] = useState(false);
  const [dismissed, setDismissed] = useState(() => {
    try {
      const at = Number(localStorage.getItem(`${storageKey}.dismissedAt`) ?? 0);
      return Date.now() - at < DISMISS_DAYS * 86_400_000;
    } catch {
      return false;
    }
  });

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    enablePush(api, storageKey, false)
      .then((next) => {
        if (!cancelled) setStatus(next);
      })
      .catch(() => {
        if (!cancelled) setStatus('off');
      });
    return () => {
      cancelled = true;
    };
    // api is a stable module object in both apps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, storageKey]);

  const turnOn = useCallback(async () => {
    setBusy(true);
    try {
      setStatus(await enablePush(api, storageKey, true));
    } catch {
      setStatus('off');
    } finally {
      setBusy(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [storageKey]);

  const dismiss = useCallback(() => {
    setDismissed(true);
    try {
      localStorage.setItem(`${storageKey}.dismissedAt`, String(Date.now()));
    } catch {
      // Dismissed for this session only.
    }
  }, [storageKey]);

  return { status, busy, turnOn, dismiss, shouldPrompt: status === 'off' && !dismissed };
}

/** Calls onPush whenever the service worker shows a notification while this app is open. */
export function usePushMessages(onPush: (message: { urgency: string; link: string | null }) => void) {
  useEffect(() => {
    if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;
    const handler = (event: MessageEvent) => {
      if (event.data?.type === 'sheout-push') onPush({ urgency: event.data.urgency, link: event.data.link ?? null });
    };
    navigator.serviceWorker.addEventListener('message', handler);
    return () => navigator.serviceWorker.removeEventListener('message', handler);
  }, [onPush]);
}

/**
 * The bell's unread count: fetched on open, when the app comes back to the
 * foreground, when a push arrives while it is open, and once a minute.
 */
export function useUnreadNotifications(fetchCount: () => Promise<number>, enabled: boolean): number {
  const [count, setCount] = useState(0);
  const refresh = useCallback(() => {
    fetchCount()
      .then(setCount)
      .catch(() => undefined);
    // fetchCount is a stable module function in both apps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!enabled) return;
    refresh();
    const timer = window.setInterval(refresh, 60_000);
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [enabled, refresh]);

  usePushMessages(useCallback(() => refresh(), [refresh]));
  return count;
}

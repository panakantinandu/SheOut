import { registerSW } from 'virtual:pwa-register';

/** How often an app left open all day looks for a new deployment. */
const CHECK_EVERY_MS = 30 * 60 * 1000;

/** A reload this soon after opening interrupts nothing: she has not started anything yet. */
const FRESH_OPEN_MS = 15 * 1000;

/**
 * Makes a new deployment reach an installed app on its next open, instead of
 * whenever the browser gets round to it.
 * <p>
 * THE PROBLEM THIS FIXES. The service worker serves the app from its cache.
 * Before, it was registered with auto-update but nothing ever asked it to
 * check for a new version or to reload once one was installed: the browser
 * only re-checks on a full navigation, which a single-page app open on a
 * phone rarely does, and even then the page already on screen came from the
 * old cache. An installed app could run a stale build for days - including
 * the build that decides whether push notifications work at all.
 * <p>
 * NOW. It checks when the app opens, every time it comes back to the
 * foreground, and every half hour while open. A new version is applied by
 * reloading - but only at a moment that interrupts nothing: within the first
 * seconds after opening, or when she returns to the app. Never in the middle
 * of a screen she is using, where a reload could drop a payment window or a
 * half-typed pickup code.
 */
export function registerAppUpdates() {
  if (!('serviceWorker' in navigator)) return;
  const openedAt = Date.now();
  let updateReady = false;

  const updateSW = registerSW({
    immediate: true,
    onNeedRefresh() {
      updateReady = true;
      if (Date.now() - openedAt < FRESH_OPEN_MS) {
        void updateSW(true);
      }
    },
    onRegisteredSW(_url, registration) {
      if (!registration) return;
      const check = () => {
        if (navigator.onLine) void registration.update();
      };
      window.setInterval(check, CHECK_EVERY_MS);
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'visible') return;
        if (updateReady) {
          void updateSW(true);
        } else {
          check();
        }
      });
    },
  });
}

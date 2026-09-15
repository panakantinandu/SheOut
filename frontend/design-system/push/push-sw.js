/* SheOut push handler - runs inside each app's service worker.
 *
 * Imported into the Workbox-generated sw.js by vite-plugin-pwa
 * (workbox.importScripts, see push/vite-plugin.mjs). Plain JavaScript with no
 * imports: a service worker cannot load the app's bundle.
 *
 * The backend sends FCM DATA-ONLY messages (see FcmPushChannel.java), so this
 * file - not Firebase - decides what the system notification looks like and
 * where tapping it goes. That is what makes it show as a real OS-level
 * notification when the app is in the background or closed.
 */

// Long-short-long, repeated: distinct from a chat ping, noticeable in a
// pocket. Only for ALERT urgency (a trip offer, an operator's SOS).
//
// PLATFORM LIMITATION, NOT A BUG: the Notification API's `vibrate` option is
// honoured by Chrome on Android only. iOS Safari and installed iOS web apps
// ignore it entirely and there is no web API that makes an iPhone vibrate;
// desktop browsers ignore it too. Do not chase this further - the sound,
// requireInteraction and renotify below are what carry the alert there.
const ALERT_VIBRATION = [600, 200, 600, 200, 900];

self.addEventListener('push', (event) => {
  event.waitUntil(handlePush(event));
});

async function handlePush(event) {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch (e) {
    payload = {};
  }
  // FCM wraps a data message as { data: {...}, from, fcmMessageId, ... }.
  const data = payload.data || payload;
  const alert = data.urgency === 'ALERT';
  const link = safeLink(data.link);

  const options = {
    body: data.body || '',
    icon: '/Logo.jpeg',
    badge: '/Logo.jpeg',
    data: { link },
    // Same tag replaces the earlier notification instead of stacking - a
    // booking's status changes collapse into its latest one.
    tag: data.tag || undefined,
    // A replaced notification still sounds and vibrates again. Without this,
    // a second offer with a reused tag would arrive silently.
    renotify: Boolean(data.tag),
    // silent:false keeps the platform's notification sound. Web notifications
    // cannot choose a custom sound on any platform; the device's own
    // notification sound is the one that plays.
    silent: false,
    // An offer or SOS stays on screen until acted on or dismissed, rather
    // than sliding away after a few seconds.
    requireInteraction: alert,
  };
  if (alert) {
    options.vibrate = ALERT_VIBRATION;
  }

  await self.registration.showNotification(data.title || 'SheOut', options);

  // An open app refreshes its inbox badge, and the partner app can chime for
  // an offer while it is in front of her.
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  for (const client of clients) {
    client.postMessage({ type: 'sheout-push', urgency: data.urgency || 'NORMAL', link });
  }
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const link = safeLink(event.notification.data && event.notification.data.link) || '/';
  event.waitUntil(openLink(link));
});

async function openLink(link) {
  const url = new URL(link, self.location.origin).href;
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  for (const client of clients) {
    if (new URL(client.url).origin === self.location.origin && 'focus' in client) {
      await client.focus();
      if ('navigate' in client) {
        return client.navigate(url);
      }
      return client;
    }
  }
  return self.clients.openWindow(url);
}

// Only ever a path on this app. A notification must never be able to send
// somebody to another site, whatever arrives in a payload.
function safeLink(link) {
  return typeof link === 'string' && link.startsWith('/') && !link.startsWith('//') ? link : null;
}

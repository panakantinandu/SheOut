/**
 * Emergency help that works from the phone itself, with no SheOut server and
 * no SMS gateway in the way.
 * <p>
 * The server texts her emergency contacts through an SMS provider. That can
 * fail - the provider unconfigured, an Indian number without DLT
 * registration, a contact abroad, no signal at the data centre - and when it
 * does she needs a way that does not depend on it. Her own phone's SMS app
 * and share sheet reach anyone, from any country, on her own plan.
 */

/**
 * The emergency number: 112, India's single number for police, fire,
 * ambulance and the women's helpline (ERSS).
 * <p>
 * Fixed, not guessed. SheOut operates only in Telangana, so this is the
 * number in every case. It used to be worked out from the phone's time zone,
 * which showed 911 on a phone set to a US zone - a number that does nothing
 * in Hyderabad. No network call either way: this has to work offline.
 */
export const EMERGENCY = { number: '112', region: 'India' } as const;

export function mapsLink(lat: number, lng: number): string {
  return `https://maps.google.com/?q=${lat.toFixed(6)},${lng.toFixed(6)}`;
}

/**
 * Opens her phone's SMS app with the numbers and message filled in. She
 * presses send; it goes from her own number, so it arrives from someone her
 * contacts know rather than from an unknown short code.
 * <p>
 * iOS and Android spell multiple recipients differently.
 */
export function openSmsComposer(phoneNumbers: string[], body: string): void {
  const numbers = phoneNumbers.map((n) => n.replace(/[^\d+]/g, '')).filter(Boolean);
  const text = encodeURIComponent(body);
  const ios = /iPad|iPhone|iPod/.test(navigator.userAgent);
  window.location.href = ios
    ? `sms:/open?addresses=${numbers.join(',')}&body=${text}`
    : `sms:${numbers.join(',')}?body=${text}`;
}

/**
 * The phone's own share sheet - WhatsApp, Messages, anything she has. False
 * when the browser has none (most desktops), so the caller can fall back.
 */
export async function shareViaDevice(title: string, text: string, url: string): Promise<'shared' | 'cancelled' | 'unsupported'> {
  if (typeof navigator.share !== 'function') return 'unsupported';
  try {
    // No url when there is none: an empty one is read as this page's own
    // address, which only works for someone signed in as her.
    await navigator.share(url ? { title, text, url } : { title, text });
    return 'shared';
  } catch (err) {
    // AbortError is her closing the sheet. Anything else - most often a lost
    // user gesture after a slow location fix - is treated as unsupported so
    // the caller shows a button she can tap directly.
    return err instanceof DOMException && err.name === 'AbortError' ? 'cancelled' : 'unsupported';
  }
}

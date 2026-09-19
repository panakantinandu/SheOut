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
 * The number to dial where she is, worked out from the phone's time zone.
 * <p>
 * The time zone, not the language setting: a phone set to English (India)
 * that is in New York is in New York, and the zone follows the phone when it
 * travels. No network call - this has to work with no connection at all.
 * 112 is the fallback because GSM phones route it to the local emergency
 * service in most of the world.
 */
export function localEmergencyNumber(): { number: string; region: string } {
  let zone = '';
  try {
    zone = Intl.DateTimeFormat().resolvedOptions().timeZone ?? '';
  } catch {
    // Very old browser - fall through to 112.
  }
  if (zone === 'Asia/Kolkata' || zone === 'Asia/Calcutta') return { number: '112', region: 'India' };
  if (NORTH_AMERICA.test(zone)) return { number: '911', region: 'North America' };
  if (zone === 'Europe/London' || zone === 'Europe/Belfast') return { number: '999', region: 'the UK' };
  if (zone.startsWith('Australia/')) return { number: '000', region: 'Australia' };
  if (zone === 'Asia/Dubai') return { number: '999', region: 'the UAE' };
  if (zone === 'Asia/Singapore') return { number: '999', region: 'Singapore' };
  if (zone.startsWith('Europe/')) return { number: '112', region: 'Europe' };
  return { number: '112', region: '' };
}

/** US, Canada and Mexico zones - all 911. */
const NORTH_AMERICA =
  /^(America\/(New_York|Chicago|Denver|Los_Angeles|Phoenix|Anchorage|Adak|Juneau|Sitka|Nome|Metlakatla|Yakutat|Boise|Detroit|Menominee|Indiana\/.+|Kentucky\/.+|North_Dakota\/.+|Toronto|Vancouver|Edmonton|Winnipeg|Halifax|St_Johns|Regina|Moncton|Glace_Bay|Goose_Bay|Whitehorse|Dawson|Dawson_Creek|Fort_Nelson|Creston|Iqaluit|Rankin_Inlet|Resolute|Cambridge_Bay|Inuvik|Swift_Current|Mexico_City|Monterrey|Merida|Cancun|Chihuahua|Hermosillo|Mazatlan|Tijuana|Bahia_Banderas|Matamoros|Ojinaga|Puerto_Rico)|Pacific\/Honolulu|US\/.+|Canada\/.+)$/;

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
    await navigator.share({ title, text, url });
    return 'shared';
  } catch (err) {
    // AbortError is her closing the sheet. Anything else - most often a lost
    // user gesture after a slow location fix - is treated as unsupported so
    // the caller shows a button she can tap directly.
    return err instanceof DOMException && err.name === 'AbortError' ? 'cancelled' : 'unsupported';
  }
}

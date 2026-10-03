import { currentLanguage } from '@sheout/design-system';

/**
 * Validity dates are whole days in India - a licence valid until the 12th is
 * good all of the 12th - whatever time zone her phone is set to.
 */
export function todayInIndia(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
}

/** Days from today (India) to an ISO date: 0 on the day, negative once it has passed. */
export function daysUntil(isoDate: string | null | undefined): number | null {
  if (!isoDate) return null;
  return Math.round((Date.parse(`${isoDate}T00:00:00Z`) - Date.parse(`${todayInIndia()}T00:00:00Z`)) / 86_400_000);
}

/** "12 Nov 2026" in her language's month names. */
export function formatDay(isoDate: string | null | undefined): string {
  if (!isoDate) return '';
  const lang = currentLanguage();
  const lng = lang === 'hi' ? 'hi-IN' : lang === 'te' ? 'te-IN' : 'en-IN';
  return new Date(`${isoDate}T00:00:00Z`).toLocaleDateString(lng, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}

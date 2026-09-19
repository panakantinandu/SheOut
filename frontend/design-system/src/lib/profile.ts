import { dsT } from '../i18n';
/**
 * Mirrors the backend's ProfileRules so the completion form can say "you
 * must be 18" before a round trip. The server is still the rule: this only
 * saves somebody a wasted submit.
 */
export const MINIMUM_AGE = 18;

/** Today as YYYY-MM-DD in India, where SheOut operates and where the server counts birthdays. */
export function todayInIndia(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(
    new Date()
  );
}

/** Whole years between a YYYY-MM-DD birth date and a YYYY-MM-DD day. */
export function ageOn(dateOfBirth: string, today: string): number {
  const [by, bm, bd] = dateOfBirth.split('-').map(Number);
  const [ty, tm, td] = today.split('-').map(Number);
  let age = ty - by;
  if (tm < bm || (tm === bm && td < bd)) age -= 1;
  return age;
}

/** The latest birth date that is 18 today - for the date input's max. */
export function latestAdultBirthDate(today = todayInIndia()): string {
  const [y, m, d] = today.split('-').map(Number);
  // 29 Feb minus 18 years may not exist; the input clamps, and the server decides.
  return `${String(y - MINIMUM_AGE).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

export function dateOfBirthProblem(dateOfBirth: string): string | null {
  if (!dateOfBirth) return dsT('validation.dobRequired', 'Enter your date of birth.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateOfBirth)) return dsT('validation.dobRequired', 'Enter your date of birth.');
  const today = todayInIndia();
  if (dateOfBirth > today || ageOn(dateOfBirth, today) > 120) return dsT('validation.dobInvalid', 'That date of birth does not look right. Check the year.');
  if (ageOn(dateOfBirth, today) < MINIMUM_AGE) return dsT('validation.dobTooYoung', 'You must be 18 or older to use SheOut.');
  return null;
}

export function emailProblem(email: string): string | null {
  if (!email.trim()) return null;
  return /^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$/.test(email.trim()) ? null : dsT('validation.emailInvalid', 'That email address does not look right.');
}

/**
 * "09:00" as she would say it, in her language: "9 am", "सुबह 9", "ఉ. 9".
 * For the verification wait line ("people check between 9 am and 9 pm").
 * An unreadable value is shown as it came, rather than as nothing.
 */
export function formatClockTime(hhmm: string | null | undefined, language: string): string {
  const match = /^(\d{1,2}):(\d{2})/.exec(hhmm ?? '');
  if (!match) return hhmm ?? '';
  const date = new Date(2000, 0, 1, Number(match[1]), Number(match[2]));
  const locale = language === 'hi' ? 'hi-IN' : language === 'te' ? 'te-IN' : 'en-IN';
  try {
    return date.toLocaleTimeString(locale, { hour: 'numeric', minute: match[2] === '00' ? undefined : '2-digit' });
  } catch {
    return hhmm ?? '';
  }
}

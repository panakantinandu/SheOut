import type { ReadinessBlocker } from '../api/types';
import { formatDay } from './documentDates';

/**
 * A reason she cannot work, in her language: "Your vehicle insurance expired
 * on 12 Nov 2026. Upload the new one to go online." Built from the code,
 * document and date the server sent rather than from its English message,
 * which is kept for the server's own refusal.
 */
export function blockerText(t: (key: string, options?: Record<string, unknown>) => string, blocker: ReadinessBlocker): string {
  return t(`docs.blocker.${blocker.code}`, {
    document: blocker.documentType ? t(`docs.type.${blocker.documentType}`) : '',
    date: formatDay(blocker.date),
    defaultValue: blocker.message,
  });
}

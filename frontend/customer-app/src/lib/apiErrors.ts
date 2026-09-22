import { currentLanguage, i18next } from '@sheout/design-system';
import { ApiError } from '../api/client';

/** Server error codes the app has its own translated wording for. */
const KNOWN_CODES = new Set([
  'UNPAID_TRIP',
  'ACTIVE_BOOKING_EXISTS',
  'CUSTOMER_PHONE_REQUIRED',
  'CUSTOMER_NOT_VERIFIED',
  'OUTSIDE_SERVICE_AREA',
  'INSUFFICIENT_BALANCE',
  'PAYMENT_NOT_VERIFIED',
  'PAYMENT_NOT_CAPTURED',
  'INVALID_AMOUNT',
  'CASH_NOT_ACCEPTED',
  'TRIP_NOT_ENDED',
  'INVALID_PICKUP_CODE',
  'PICKUP_CODE_REQUIRED',
  'PICKUP_VERIFICATION_LOCKED',
]);

/**
 * What to show for a failed request, in her language.
 * <p>
 * The server writes its messages in English. Where the error has a code the
 * app knows, the app's own translation is used. Otherwise English readers
 * get the server's sentence, which is usually the most specific; readers of
 * other languages get a translated sentence with the server's detail after
 * it, rather than only English they may not read.
 */
export function apiErrorText(err: unknown, fallbackKey: string): string {
  const fallback = i18next.t(fallbackKey);
  if (!(err instanceof ApiError)) return fallback;
  const code = err.body?.error;
  if (code && KNOWN_CODES.has(code)) return i18next.t(`apiError.${code}`);
  if (currentLanguage() === 'en') return err.message || fallback;
  return err.message ? `${fallback} (${err.message})` : fallback;
}

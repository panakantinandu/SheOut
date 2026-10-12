import { currentLanguage, i18next, networkErrorText, serverErrorText } from '@sheout/design-system';
import { ApiError } from '../api/client';

/** Server error codes the app has its own translated wording for. */
const KNOWN_CODES = new Set([
  'INVALID_PARCEL_DELIVERY_CODE',
  'PARCEL_DELIVERY_CODE_REQUIRED',
  'PARCEL_DELIVERY_CODE_LOCKED',
  'PARCEL_DELIVERY_PHOTO_REQUIRED',
  'PARCEL_PICKUP_PHOTO_REQUIRED',
  'PARCEL_EXCEPTION_PHOTO_REQUIRED',
  'PARCEL_EXCEPTION_NOTE_REQUIRED',
  'PARCEL_PHOTO_INVALID',
  'DRIVER_NOT_AT_DROP_OFF',
  'UPI_QR_UNAVAILABLE',
  'REFERRAL_CODE_INVALID',
  'REFERRAL_OWN_CODE',
  'REFERRAL_ALREADY_APPLIED',
  'REFERRAL_NOT_ELIGIBLE',
  'UNPAID_TRIP',
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
  'DESTINATION_CHANGE_NOT_PENDING',
  'SHIFT_CHECK_EXPIRED',
  'SHIFT_CHECK_UNDER_REVIEW',
  'SELFIE_REQUIRED',
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
  // Already in her language: the phone could not reach SheOut, or the server failed without saying why.
  if (err.message === networkErrorText() || err.message === serverErrorText()) return err.message;
  // The server writes its other messages in English, for people. In Hindi or
  // Telugu the screen's own line is shown instead of English in brackets.
  if (currentLanguage() === 'en') return err.message || fallback;
  return fallback;
}

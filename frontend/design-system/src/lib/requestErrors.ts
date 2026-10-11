import { dsT } from '../i18n';

/**
 * What a failed call says to her, when the server's own words are not fit to
 * show: the phone could not reach SheOut at all, or the server failed in a
 * way it did not explain for people (a 5xx with only an HTTP reason such as
 * "Bad Gateway", or a body that is not JSON - a proxy's HTML error page).
 * In her language, never "Request failed (500)" or "Failed to fetch".
 * <p>
 * A 4xx message is kept: those are written for people ("This number already
 * has a SheOut customer account..."). So is a 5xx that names its own error
 * code (IMAGE_STORAGE_FAILED, PAYMENT_FAILED): its message was written for
 * her too.
 */
export function networkErrorText(): string {
  return dsT('requestError.network', "Couldn't reach SheOut. Check your internet and try again.");
}

export function serverErrorText(): string {
  return dsT('requestError.server', 'Something went wrong on our side. Please try again in a moment.');
}

/** The message an ApiError should carry for this response. */
export function requestErrorMessage(status: number, body: { error?: string; message?: string } | undefined): string {
  const namedCode = typeof body?.error === 'string' && /^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$/.test(body.error);
  if (body?.message && (status < 500 || namedCode)) return body.message;
  return serverErrorText();
}

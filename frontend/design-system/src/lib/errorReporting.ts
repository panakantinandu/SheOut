import * as Sentry from '@sentry/react';

/**
 * Crash reporting for both apps.
 * <p>
 * Until now a crash on somebody's phone was invisible here: she saw a blank
 * screen, closed the app, and nothing reached anybody. This sends the
 * exception and its stack so the same crash can be seen and fixed.
 * <p>
 * IT IS OFF UNLESS A DSN IS SET. No VITE_SENTRY_DSN, no client, no network
 * calls - which is what local development and any fork of this repo get.
 * <p>
 * WHAT IS DELIBERATELY NOT SENT. No performance tracing and no session
 * replay: replay records what somebody types and sees, which on these two
 * apps means her home address, her live location and her chat with a
 * stranger. Personal data is stripped from what is left - see scrub - and
 * sendDefaultPii stays off, so no IP address travels with a report either.
 */
export interface ErrorReportingOptions {
  /** From VITE_SENTRY_DSN at build time. Blank or missing turns everything here off. */
  dsn?: string;
  /** Which deployment this is, so a local test is not mixed in with real users' crashes. */
  environment?: string;
}

/** An E.164-ish number - the same shape the backend's Redact matches. */
const PHONE_IN_TEXT = /\+?\d[\d -]{6,17}\d/g;

/** "+919876543210" becomes "+91********10": enough to tie two reports together, not enough to identify anybody. */
function maskPhone(value: string): string {
  const trimmed = value.trim();
  if (trimmed.length <= 5) return '*'.repeat(trimmed.length);
  return trimmed.slice(0, 3) + '*'.repeat(trimmed.length - 5) + trimmed.slice(-2);
}

function redact(text: string | undefined): string | undefined {
  return text?.replace(PHONE_IN_TEXT, maskPhone);
}

/**
 * Starts crash reporting, if a DSN was built in.
 * Returns whether it is actually on, which is useful in a console line and
 * nowhere else - nothing in either app should behave differently either way.
 */
export function initErrorReporting({ dsn, environment }: ErrorReportingOptions): boolean {
  if (!dsn) return false;

  Sentry.init({
    dsn,
    environment: environment || 'production',
    // Errors only. Tracing samples ordinary successful page loads, which is
    // a different quota spent on nothing anybody here is asking about.
    tracesSampleRate: 0,
    sendDefaultPii: false,
    beforeSend(event) {
      if (event.message) event.message = redact(event.message) ?? event.message;
      event.exception?.values?.forEach((value) => {
        value.value = redact(value.value);
      });
      // The URL of the screen she was on. Ours carry ids, not numbers, but a
      // query string is the classic place one ends up.
      if (event.request?.url) event.request.url = redact(event.request.url);
      return event;
    },
    beforeBreadcrumb(crumb) {
      crumb.message = redact(crumb.message);
      if (typeof crumb.data?.url === 'string') crumb.data.url = redact(crumb.data.url);
      return crumb;
    },
  });
  return true;
}

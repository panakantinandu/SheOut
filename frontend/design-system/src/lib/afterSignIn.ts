/**
 * Where sign-in may send her next, other than Home. Only paths on this list
 * are honoured, so a crafted link cannot route a fresh sign-in anywhere
 * else. Today: the delete step, reached from the public Delete your account
 * page (DeleteAccountInfo) - the sign-in is its identity check.
 */
export const DELETE_ACCOUNT_STEP = '/settings?delete=1';
const ALLOWED_NEXT = new Set<string>([DELETE_ACCOUNT_STEP]);

/** The `next` path a screen asked sign-in to return to, if it is allowed; otherwise Home. */
export function afterSignInPath(state: unknown, fallback = '/home'): string {
  const next = (state as { next?: unknown } | null)?.next;
  return typeof next === 'string' && ALLOWED_NEXT.has(next) ? next : fallback;
}

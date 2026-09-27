/**
 * Referral links and this install's id - shared by both apps.
 * <p>
 * A friend's link is the app's address with ?ref=CODE. It is read once, as
 * the app starts, and kept until she has finished setting up her account, so
 * the code is waiting in the signup field however many screens (sign-in, the
 * OTP, the splash) come in between.
 * <p>
 * The install id is a random id this app keeps in its own storage and sends
 * with the referral calls only. It is how the server recognises a friend
 * entering a code from the same phone the code was shared from. Clearing the
 * browser makes a new one; it is a check against the obvious case, not a
 * fingerprint.
 */

const REF_KEY = 'sheout_referral_code';
const INSTALL_KEY = 'sheout_install_id';
const CODE = /^[A-Za-z0-9]{4,12}$/;

/** Call once as the app starts, before the router reads the URL. */
export function captureReferralFromUrl(): void {
  if (typeof window === 'undefined') return;
  try {
    const url = new URL(window.location.href);
    const ref = url.searchParams.get('ref');
    if (!ref) return;
    if (CODE.test(ref)) localStorage.setItem(REF_KEY, ref.toUpperCase());
    url.searchParams.delete('ref');
    window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
  } catch {
    // Storage blocked: she can still type the code in.
  }
}

/** The code from a friend's link, if she opened one. Empty when none. */
export function pendingReferralCode(): string {
  try {
    return localStorage.getItem(REF_KEY) ?? '';
  } catch {
    return '';
  }
}

export function clearPendingReferralCode(): void {
  try {
    localStorage.removeItem(REF_KEY);
  } catch {
    // Nothing to clear.
  }
}

/** This install's id, made on first use. Null when storage is blocked. */
export function installId(): string | null {
  try {
    let id = localStorage.getItem(INSTALL_KEY);
    if (!id) {
      id = typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
      localStorage.setItem(INSTALL_KEY, id);
    }
    return id;
  } catch {
    return null;
  }
}

/** What GET /api/v1/referrals/me returns - the same for riders and partners. */
export interface ReferralSummary {
  code: string;
  shareUrl: string;
  role: 'CUSTOMER' | 'DRIVER';
  /** Friends who joined with her code and have taken (or driven) a first paid trip. */
  successful: number;
  /** Of those, the ones that earned her a reward - at most maxRewarded. */
  rewarded: number;
  maxRewarded: number;
  totalEarned: number;
  /** Friends who joined and have not had a paid trip yet. */
  pending: number;
  /** What the programme gives each side now; null while that side is paused, ended or out of budget. */
  referrerAmount: number | null;
  refereeAmount: number | null;
  /** Partners are paid into their wallet; riders get ride credit. */
  cashReward: boolean;
  /** Whether this account may still enter a friend's code. */
  canApplyCode: boolean;
  joinedWith: { status: 'PENDING' | 'COMPLETED' | 'REJECTED'; reward: number | null } | null;
}

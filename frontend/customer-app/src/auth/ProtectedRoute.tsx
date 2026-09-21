import { useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from './AuthContext';

/**
 * Screens a rider must always be able to reach, whatever step she is on:
 * somebody mid-trip on an account from before dates of birth were required
 * must still see her trip and press SOS without being stopped by a form.
 */
const ALWAYS_REACHABLE = ['/add-phone', '/complete-profile', '/sos', '/tracking/', '/chat/', '/help', '/notifications'];

/**
 * What still stands between this account and the app, in order.
 * <p>
 * 'phone' first: an account made through Google has no number, and a rider
 * without one cannot be reached by her partner, by SOS or by support - so
 * it is asked for before anything else, and nothing past this gate is
 * reachable until a real number has been verified by code. Then 'profile',
 * the name, date of birth and photo every account gives. The server refuses
 * to book for an account without a number as well; this is the part she
 * sees.
 */
type Gate = 'phone' | 'profile' | 'open';

export function gateFor(profile: Pick<CustomerProfileSummary, 'phoneNumber' | 'profileComplete'>): Gate {
  if (!profile.phoneNumber) return 'phone';
  if (!profile.profileComplete) return 'profile';
  return 'open';
}

/**
 * Known answer for this account, kept for the life of the page so every
 * screen does not refetch it. Moved on by the two steps as they finish.
 */
let gateForAccount: { accountId: string; gate: Gate } | null = null;

/**
 * Guards currently on screen.
 * <p>
 * React reuses this component across routes - every protected route renders
 * the same guard in the same place in the tree - so a guard that already
 * decided keeps that decision when she moves to the next screen, and the
 * initial state is never read again. Without this, finishing a step bounced
 * straight back to it until the app was reloaded.
 */
const watchers = new Set<(gate: Gate | null) => void>();

function setGate(accountId: string, gate: Gate | null) {
  gateForAccount = gate ? { accountId, gate } : null;
  watchers.forEach((tell) => tell(gate));
}

/**
 * The profile step is done. Open only if the profile was known to be the
 * last step left; otherwise asked again, because a profile finished before
 * anything was checked says nothing about whether a number is on file.
 */
export function markProfileComplete(accountId: string) {
  const known = gateForAccount?.accountId === accountId ? gateForAccount.gate : null;
  setGate(accountId, known === 'profile' || known === 'open' ? 'open' : null);
}

/** The phone step is done; the profile step is next unless it was already finished. */
export function markPhoneAdded(accountId: string, profileComplete: boolean) {
  setGate(accountId, profileComplete ? 'open' : 'profile');
}

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, accountId } = useAuth();
  const location = useLocation();
  const cached = gateForAccount?.accountId === accountId ? gateForAccount.gate : null;
  const [gate, setGateState] = useState<Gate | null>(cached);
  const exempt = ALWAYS_REACHABLE.some((path) => location.pathname.startsWith(path));

  useEffect(() => {
    const tell = (known: Gate | null) => setGateState(known);
    watchers.add(tell);
    return () => {
      watchers.delete(tell);
    };
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !accountId || gate !== null) return;
    let cancelled = false;
    usersApi
      .getMyProfile()
      .then((profile) => {
        const next = gateFor(profile);
        gateForAccount = { accountId, gate: next };
        if (!cancelled) setGateState(next);
      })
      // If the profile cannot be read, do not trap her on a form she may not
      // be able to save either: let her in, and the next load checks again.
      // Booking is still refused by the server for an account with no number.
      .catch(() => {
        if (!cancelled) setGateState('open');
      });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, accountId, gate]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (!exempt && gate === 'phone') {
    return <Navigate to="/add-phone" replace />;
  }
  if (!exempt && gate === 'profile') {
    return <Navigate to="/complete-profile" replace />;
  }
  if (!exempt && gate === null) {
    return <p className="p-6 text-center text-sm text-text-secondary">Loading...</p>;
  }
  return <>{children}</>;
}

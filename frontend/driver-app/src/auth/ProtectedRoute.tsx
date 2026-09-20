import { useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { usersApi } from '../api/client';
import { useAuth } from './AuthContext';

/**
 * Screens a partner must always be able to reach, profile complete or not:
 * somebody mid-trip on an account from before dates of birth were required
 * must still finish her trip, and see the offer she was sent, without being
 * stopped by a form.
 */
const ALWAYS_REACHABLE = ['/complete-profile', '/trip/', '/offer/', '/chat/', '/help', '/notifications'];

/**
 * Known answer to "is this account's profile complete", kept for the life of
 * the page so every screen does not refetch it. Set true by the completion
 * screen once it saves.
 */
let profileCompleteForAccount: { accountId: string; complete: boolean } | null = null;

/**
 * Guards currently on screen.
 * <p>
 * React reuses this component across routes - every protected route renders
 * the same guard in the same place in the tree - so a guard that already
 * decided "not complete" keeps that decision when she moves to the next
 * screen, and the initial state is never read again. Without this, finishing
 * the profile form bounced straight back to it until the app was reloaded.
 */
const watchers = new Set<(known: boolean) => void>();

export function markProfileComplete(accountId: string) {
  profileCompleteForAccount = { accountId, complete: true };
  watchers.forEach((tell) => tell(true));
}

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, accountId } = useAuth();
  const location = useLocation();
  const cached = profileCompleteForAccount?.accountId === accountId ? profileCompleteForAccount.complete : null;
  const [complete, setComplete] = useState<boolean | null>(cached);
  const exempt = ALWAYS_REACHABLE.some((path) => location.pathname.startsWith(path));

  useEffect(() => {
    const tell = (known: boolean) => setComplete(known);
    watchers.add(tell);
    return () => {
      watchers.delete(tell);
    };
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !accountId || complete !== null) return;
    let cancelled = false;
    usersApi
      .getMyProfile()
      .then((profile) => {
        profileCompleteForAccount = { accountId, complete: profile.profileComplete };
        if (!cancelled) setComplete(profile.profileComplete);
      })
      // If the profile cannot be read, do not trap her on a form she may not
      // be able to save either: let her in, and the next load checks again.
      .catch(() => {
        if (!cancelled) setComplete(true);
      });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, accountId, complete]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (!exempt && complete === false) {
    return <Navigate to="/complete-profile" replace />;
  }
  if (!exempt && complete === null) {
    return <p className="p-6 text-center text-sm text-text-secondary">Loading...</p>;
  }
  return <>{children}</>;
}

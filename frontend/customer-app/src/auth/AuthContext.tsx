import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { authApi, getStoredToken } from '../api/client';
import type { AuthSession } from '../api/types';

interface AuthState {
  accountId: string | null;
  isAuthenticated: boolean;
  login(session: AuthSession): void;
  logout(): void;
}

const AuthContext = createContext<AuthState | null>(null);

const ACCOUNT_ID_KEY = 'sheout_account_id';

function readStoredAccountId(): string | null {
  try {
    return localStorage.getItem(ACCOUNT_ID_KEY);
  } catch {
    return null;
  }
}

/**
 * Optimistic auth: presence of a stored token/accountId means "treat as
 * logged in" - there's no /auth/me endpoint to validate against on load.
 * If the token is actually expired/invalid, the first real API call fails
 * with 401 and screens redirect to /login from there (see ProtectedRoute).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [accountId, setAccountId] = useState<string | null>(() =>
    getStoredToken() ? readStoredAccountId() : null
  );

  const value = useMemo<AuthState>(
    () => ({
      accountId,
      isAuthenticated: accountId !== null,
      login(session) {
        try {
          localStorage.setItem(ACCOUNT_ID_KEY, session.accountId);
        } catch {
          // Storage disabled - session state still works for this tab via React state.
        }
        setAccountId(session.accountId);
      },
      logout() {
        authApi.logout();
        try {
          localStorage.removeItem(ACCOUNT_ID_KEY);
        } catch {
          // ignore
        }
        setAccountId(null);
      },
    }),
    [accountId]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

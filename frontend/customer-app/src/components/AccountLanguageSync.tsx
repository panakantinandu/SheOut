import { useEffect, type ReactNode } from 'react';
import { syncLanguageWithAccount } from '@sheout/design-system';
import { preferencesApi } from '../api/client';
import { useAuth } from '../auth/AuthContext';

/**
 * Keeps the app's language in step with her account: once she is signed
 * in, a choice made on this phone is saved to the account, and otherwise
 * the account's choice is applied - see syncLanguageWithAccount.
 * <p>
 * This used to live in the side-menu drawer, which has gone: it repeated
 * the Profile tab entry for entry, so every destination had two ways in.
 */
export function AccountLanguageSync({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  useEffect(() => {
    if (!isAuthenticated) return;
    syncLanguageWithAccount(preferencesApi).catch(() => undefined);
  }, [isAuthenticated]);
  return <>{children}</>;
}

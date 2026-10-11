import { useEffect, useRef } from 'react';

/**
 * The phone's back button on a screen with nothing safe behind it - the
 * first screen after sign-in, where the entry before it is gone (sign-in
 * replaced itself) and back would simply close the app.
 * <p>
 * While enabled, one extra history entry sits on top of this screen. Back
 * takes that entry instead of leaving, and onBack decides what back means
 * here - usually a question ("Use a different number?") rather than an
 * exit. The entry is put back each time, so back keeps asking.
 * <p>
 * window.history directly, not the router: shared by both apps, and the
 * extra entry carries the router's own state, so the router sees the same
 * screen and does nothing.
 */
export function useBackGuard(onBack: () => void, enabled = true) {
  const latest = useRef(onBack);
  latest.current = onBack;

  useEffect(() => {
    if (!enabled) return;
    const arm = () => window.history.pushState({ ...(window.history.state ?? {}), backGuard: true }, '');
    arm();
    const onPop = () => {
      arm();
      latest.current();
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, [enabled]);
}

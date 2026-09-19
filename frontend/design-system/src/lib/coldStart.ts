/**
 * Making the splash screen show on every launch from the home-screen icon.
 * <p>
 * Two things were skipping it. The manifests opened the installed app at
 * /home, which Splash never mounts on: signed out, /home redirected straight
 * to /login; signed in, it was Home itself. start_url now points at "/" -
 * but an app already installed keeps the manifest it was installed with
 * until the browser gets round to refreshing it, which can take days. So
 * this also catches a cold launch at any other path, in the installed app,
 * and sends it through Splash first, carrying where it was going.
 * <p>
 * A cold launch is a page load in a fresh browsing session. sessionStorage
 * is empty then and survives reloads within the session, so a service
 * worker update that reloads the page mid-use does not replay the splash.
 * Nothing is remembered between launches - there is no "seen the splash
 * already" flag, because a real app shows it every time it starts.
 */

const SESSION_KEY = 'sheout_session_started';

/** Installed and opened from its icon, rather than in a browser tab. */
export function isStandaloneLaunch(): boolean {
  if (typeof window === 'undefined') return false;
  const iosStandalone = (window.navigator as Navigator & { standalone?: boolean }).standalone === true;
  return iosStandalone || window.matchMedia?.('(display-mode: standalone)').matches === true;
}

/**
 * Call once, before the router reads the URL. If this is the installed app
 * starting cold anywhere but "/", rewrite the URL to "/?next=<where it was>"
 * so Splash runs first and then continues there.
 */
export function routeColdStartThroughSplash(): void {
  if (typeof window === 'undefined') return;
  let alreadyRunning = false;
  try {
    alreadyRunning = sessionStorage.getItem(SESSION_KEY) === '1';
    sessionStorage.setItem(SESSION_KEY, '1');
  } catch {
    // Storage blocked: treat every load as a cold start, which only means
    // the splash also shows on a reload.
  }
  if (alreadyRunning || !isStandaloneLaunch()) return;
  const { pathname, search, hash } = window.location;
  if (pathname === '/') return;
  window.history.replaceState(window.history.state, '', `/?next=${encodeURIComponent(pathname + search + hash)}`);
}

/**
 * Where Splash goes when it finishes. Signed out, always Login. Signed in,
 * wherever the launch was headed (a notification's deep link, say), or
 * Home. Only same-app paths are followed - never another origin, never back
 * to the splash or the login screen.
 */
export function splashDestination(search: string, isAuthenticated: boolean): string {
  if (!isAuthenticated) return '/login';
  const next = new URLSearchParams(search).get('next');
  if (next && next.startsWith('/') && !next.startsWith('//') && next !== '/' && !next.startsWith('/login')) {
    return next;
  }
  return '/home';
}

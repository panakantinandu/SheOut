// Minimal ambient typing for the bits of Google Identity Services (GIS) this
// file actually calls - GIS ships no official TS types, and pulling in a
// full @types package for two methods isn't worth it.
declare global {
  interface Window {
    google?: {
      accounts: {
        oauth2: {
          initTokenClient(config: {
            client_id: string;
            scope: string;
            callback: (response: { access_token?: string; error?: string }) => void;
          }): { requestAccessToken(): void };
        };
      };
    };
  }
}

const GIS_SCRIPT_SRC = 'https://accounts.google.com/gsi/client';

let scriptLoadPromise: Promise<void> | null = null;

function loadGisScript(): Promise<void> {
  if (scriptLoadPromise) return scriptLoadPromise;
  scriptLoadPromise = new Promise((resolve, reject) => {
    if (window.google?.accounts?.oauth2) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.src = GIS_SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Could not load Google Identity Services - check your connection'));
    document.head.appendChild(script);
  });
  return scriptLoadPromise;
}

/**
 * CHANGED FROM google.accounts.id (One Tap) TO google.accounts.oauth2
 * (the OAuth2 popup flow) - One Tap's prompt() is meant for automatic,
 * passive prompts, not a button click, and Google's own docs point a
 * custom-triggered "Continue with Google" button at this API instead.
 * In practice, One Tap's prompt() also silently declines to show at all
 * for reasons it won't fully report (no active Google session in the
 * browser, third-party-cookie/FedCM restrictions in current Chrome, a
 * previous dismissal, ...) - exactly the "closed, or unavailable in this
 * browser" failure this app hit. A real user-gesture-triggered popup
 * (this function is called directly from the button's onClick) doesn't
 * have that failure mode - browsers only block *programmatic* popups
 * with no direct user gesture behind them.
 * <p>
 * Returns an OAuth2 access token, not an ID token (JWT) - the backend
 * verifies it by calling Google's own tokeninfo endpoint (confirms the
 * token was actually issued for this app's Client ID, not just any
 * Google token) and then the userinfo endpoint (fetches the verified
 * email/name) rather than checking a JWT signature locally. Both are
 * equally valid ways to establish trust; this one is simply the correct
 * fit for a popup-triggered flow. See GoogleTokenVerifier on the backend.
 */
export function signInWithGoogle(clientId: string): Promise<string> {
  return loadGisScript().then(
    () =>
      new Promise<string>((resolve, reject) => {
        if (!window.google) {
          reject(new Error('Google Identity Services unavailable'));
          return;
        }
        const client = window.google.accounts.oauth2.initTokenClient({
          client_id: clientId,
          scope: 'openid email profile',
          callback: (response) => {
            if (response.access_token) resolve(response.access_token);
            else reject(new Error(response.error || 'Google sign-in was cancelled'));
          },
        });
        client.requestAccessToken();
      })
  );
}

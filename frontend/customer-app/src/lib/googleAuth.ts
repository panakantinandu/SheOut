// Minimal ambient typing for the bits of Google Identity Services (GIS) this
// file actually calls - GIS ships no official TS types, and pulling in a
// full @types package for four methods isn't worth it.
declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(config: { client_id: string; callback: (response: { credential?: string }) => void }): void;
          prompt(momentListener?: (notification: GsiMoment) => void): void;
        };
      };
    };
  }
}

interface GsiMoment {
  isNotDisplayed(): boolean;
  isSkippedMoment(): boolean;
}

const GIS_SCRIPT_SRC = 'https://accounts.google.com/gsi/client';

let scriptLoadPromise: Promise<void> | null = null;

function loadGisScript(): Promise<void> {
  if (scriptLoadPromise) return scriptLoadPromise;
  scriptLoadPromise = new Promise((resolve, reject) => {
    if (window.google?.accounts?.id) {
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
 * Triggers Google's One Tap credential prompt from our own custom-styled
 * button rather than rendering Google's own branded button - both surfaces
 * share the same underlying google.accounts.id API. Resolves with the raw
 * ID token JWT on success; the backend verifies it server-side, this
 * frontend code never inspects or trusts its contents.
 * <p>
 * KNOWN GIS LIMITATION, NOT A BUG HERE: prompt() can silently decline to
 * show anything at all (third-party cookies blocked, user previously
 * dismissed One Tap, unsupported browser, ...) without an error by
 * default - the moment listener below turns that into a rejected promise
 * instead of leaving the caller hanging forever.
 */
export function signInWithGoogle(clientId: string): Promise<string> {
  return loadGisScript().then(
    () =>
      new Promise<string>((resolve, reject) => {
        if (!window.google) {
          reject(new Error('Google Identity Services unavailable'));
          return;
        }
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => {
            if (response.credential) resolve(response.credential);
            else reject(new Error('Google sign-in did not return a credential'));
          },
        });
        window.google.accounts.id.prompt((notification) => {
          if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
            reject(new Error('Google sign-in was closed, or is unavailable in this browser'));
          }
        });
      })
  );
}

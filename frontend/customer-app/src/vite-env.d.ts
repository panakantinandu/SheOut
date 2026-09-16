/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /**
   * Sentry project DSN, set in the Vercel project's environment. Unset means
   * crash reporting is off entirely - see initErrorReporting.
   */
  readonly VITE_SENTRY_DSN?: string;
  /** Google Cloud OAuth Client ID for Sign-In - unset until a real one exists, see Login.tsx. */
  readonly VITE_GOOGLE_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

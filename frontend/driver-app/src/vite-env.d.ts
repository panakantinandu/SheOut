/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /**
   * Sentry project DSN, set in the Vercel project's environment. Unset means
   * crash reporting is off entirely - see initErrorReporting.
   */
  readonly VITE_SENTRY_DSN?: string;
  /**
   * 'true' mounts the live map card on Home while she is online. On in
   * staging, off in production until its map-load cost is known.
   */
  readonly VITE_DRIVER_HOME_MAP_ENABLED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

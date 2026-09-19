/**
 * Lets the shared components import image assets directly, so a consuming
 * app does not need its own copy in public/. Vite resolves these to a
 * hashed URL at build time; both apps consume this package as source, so
 * they inherit the same asset without duplicating the file.
 */
declare module '*.png' {
  const src: string;
  export default src;
}

/**
 * The map settings, read from whichever app is bundling this package.
 * <p>
 * Declared here rather than pulled in via `vite/client` types: this package
 * is consumed as source by both apps and has no Vite config of its own, so
 * naming the two variables it actually reads keeps the contract visible and
 * avoids widening every env lookup in here to `any`. See LiveMap for what
 * they are for and what to set them to.
 */
interface ImportMetaEnv {
  /** Google Maps JavaScript API key, restricted to the apps' own sites. */
  readonly VITE_GOOGLE_MAPS_API_KEY?: string;
  /** Optional Cloud Console map style; replaces the built-in JSON style. */
  readonly VITE_GOOGLE_MAPS_MAP_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

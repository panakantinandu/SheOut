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

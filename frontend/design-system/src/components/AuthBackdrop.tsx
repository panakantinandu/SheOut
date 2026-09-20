/**
 * The moving colour behind the sign-in screens.
 * <p>
 * Two soft washes of the brand's purple and orange, drifting slowly across
 * the gradient. The sign-in screen was a flat wash with a form on it - correct,
 * and completely still; this gives it depth without putting anything in front
 * of the one thing she came here to do.
 * <p>
 * Radial gradients rather than blurred shapes, and only `transform` is
 * animated: a blur this size is repainted every frame on a cheap phone, while
 * a transform is handed to the compositor. Purely decorative, so it is hidden
 * from screen readers, and motion-safe, so it simply sits still for anybody
 * who asked for less movement.
 */
export function AuthBackdrop() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      <span className="absolute -left-24 -top-16 h-72 w-72 rounded-full bg-[radial-gradient(circle,rgba(74,26,158,0.18),transparent_70%)] motion-safe:animate-drift" />
      <span className="absolute -right-24 top-1/3 h-80 w-80 rounded-full bg-[radial-gradient(circle,rgba(249,103,23,0.16),transparent_70%)] motion-safe:animate-drift-slow" />
      <span className="absolute -bottom-24 left-1/4 h-72 w-72 rounded-full bg-[radial-gradient(circle,rgba(26,182,90,0.10),transparent_70%)] motion-safe:animate-drift" />
    </div>
  );
}

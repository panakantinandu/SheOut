/**
 * "Sample screens" on every phone and video made from the apps. They were
 * shot with sample data (names, trips, a rating), so they must never read
 * as real customers or real results.
 */
export function SampleLabel({ className = '' }: { className?: string }) {
  return (
    <span
      className={`pointer-events-none absolute z-10 rounded-full bg-black/75 px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-white ${className}`}
      data-testid="sample-label"
    >
      Sample screens
    </span>
  );
}

/**
 * A two-wheeler helmet, side on, visor forward - drawn to sit with the
 * lucide icons both apps use (24 grid, 2px stroke, round joins). lucide has
 * no helmet, and its hard hat reads as a building site, not a ride.
 */
export function HelmetIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M3 17c0-6.3 4.4-11 10-11 4.4 0 8 3.3 8 8v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <path d="M21 11h-7.5a2 2 0 0 0-2 2v0a2 2 0 0 0 2 2H21" />
      <path d="M7 9.5c1-1.3 2.4-2.2 4-2.5" />
    </svg>
  );
}

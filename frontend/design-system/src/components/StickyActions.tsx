import type { ReactNode } from 'react';

/**
 * The actions a screen exists for, pinned to the bottom of the screen while
 * everything above them scrolls: Cancel ride while she waits, Accept while a
 * request counts down. Put last in the page. They used to sit after a map
 * and a few cards, so on a 640 px phone she had to scroll to find Cancel -
 * and a partner, to find Accept inside fifteen seconds.
 * <p>
 * Sticky, not fixed: it takes its own place at the end of the page, so it
 * never hides the last card, and it clears the phone's gesture bar. -mb-12
 * takes back PageShell's bottom padding, so it sits flush at the end too.
 */
export function StickyActions({ children, testId, pinned = true }: { children: ReactNode; testId?: string; pinned?: boolean }) {
  // Unpinned: the same actions in the page's own flow, for a moment when
  // something on the screen matters more than they do and must not be
  // covered (the identity check at the kerb).
  if (!pinned) return <div data-testid={testId ?? 'sticky-actions'} data-pinned="false">{children}</div>;
  return (
    <div
      className="sticky bottom-0 z-20 -mx-screen -mb-12 border-t border-border bg-background px-screen pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] shadow-[0_-8px_20px_rgba(16,10,26,0.06)]"
      data-testid={testId ?? 'sticky-actions'}
      data-pinned="true"
    >
      {children}
    </div>
  );
}

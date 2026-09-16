import type { ReactNode } from 'react';

export interface PageTransitionProps {
  /** Changes when the screen changes - the route path. Remounting is what restarts the animation. */
  transitionKey: string;
  children: ReactNode;
}

/**
 * The small fade-and-rise a screen does when it arrives.
 * <p>
 * Two hundred milliseconds and six pixels: enough that moving between
 * screens reads as movement rather than a hard cut, not enough that anybody
 * waits for it. There is no exit animation, deliberately - holding the old
 * screen while the new one fades in would put a real delay between a tap and
 * the thing she tapped for, and this app is used one-handed, at night,
 * sometimes in a hurry.
 * <p>
 * A key change, not an animation library: React remounts the subtree and the
 * CSS animation runs once. The global reduced-motion rule removes it.
 */
export function PageTransition({ transitionKey, children }: PageTransitionProps) {
  return (
    <div key={transitionKey} className="animate-fade-slide-in motion-reduce:animate-none">
      {children}
    </div>
  );
}

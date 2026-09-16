import { useRef, useState, type ReactNode, type TouchEvent } from 'react';
import { RefreshCw } from 'lucide-react';
import { cn } from '../lib/cn';

export interface PullToRefreshProps {
  /** Reloads whatever the screen shows. Resolves when it is done; a rejection still ends the pull. */
  onRefresh: () => void | Promise<unknown>;
  children: ReactNode;
  /** Off while a screen has nothing to refresh, e.g. it is still loading for the first time. */
  disabled?: boolean;
  className?: string;
}

/** How far she has to pull before letting go refreshes. Short enough to discover, long enough not to fire by accident. */
const TRIGGER_PX = 64;

/** Past this the indicator stops following the finger, so a hard pull does not drag the screen off. */
const MAX_PULL_PX = 96;

/**
 * Pull down at the top of a list to reload it.
 * <p>
 * The gesture everybody already has in their fingers: these are lists of
 * things that change while you are looking at them - a trip that completed,
 * a payout that landed, a notification that arrived - and the alternative is
 * hunting for a refresh button or closing and reopening the app.
 * <p>
 * Only starts when the scroll container is genuinely at the top, so it never
 * fights a normal scroll. It does not preventDefault, so if the browser
 * would rather do its own native pull-to-refresh it still can; this just
 * makes the in-app one work where it would not.
 * <p>
 * Touch only. A mouse has no pull gesture, and every screen using this keeps
 * whatever explicit reload it already had.
 */
export function PullToRefresh({ onRefresh, children, disabled = false, className }: PullToRefreshProps) {
  const [pull, setPull] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const startY = useRef<number | null>(null);

  function atTop(): boolean {
    // The page itself scrolls in both apps; a nested scroller would report
    // its own position, which is why this asks the document.
    return window.scrollY <= 0;
  }

  function onTouchStart(e: TouchEvent) {
    if (disabled || refreshing || !atTop()) return;
    startY.current = e.touches[0].clientY;
  }

  function onTouchMove(e: TouchEvent) {
    if (startY.current === null) return;
    const distance = e.touches[0].clientY - startY.current;
    if (distance <= 0 || !atTop()) {
      setPull(0);
      return;
    }
    // Resistance: the further she pulls, the less it follows, so the end of
    // the gesture is felt rather than guessed at.
    setPull(Math.min(MAX_PULL_PX, distance * 0.5));
  }

  async function onTouchEnd() {
    const pulled = pull;
    startY.current = null;
    setPull(0);
    if (pulled < TRIGGER_PX || refreshing) return;
    setRefreshing(true);
    try {
      await onRefresh();
    } catch {
      // The screen shows its own error. This only owns the spinner.
    } finally {
      setRefreshing(false);
    }
  }

  const showing = refreshing || pull > 0;

  return (
    <div
      className={className}
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
      onTouchCancel={onTouchEnd}
    >
      <div
        className="flex items-center justify-center overflow-hidden transition-[height] duration-150 motion-reduce:transition-none"
        style={{ height: refreshing ? 36 : pull }}
        aria-hidden={!refreshing}
      >
        {showing && (
          <RefreshCw
            className={cn('h-4 w-4 text-text-secondary', refreshing && 'animate-spin motion-reduce:animate-none')}
            style={refreshing ? undefined : { transform: `rotate(${(pull / TRIGGER_PX) * 180}deg)` }}
          />
        )}
      </div>
      {refreshing && (
        <span className="sr-only" role="status">
          Refreshing
        </span>
      )}
      {children}
    </div>
  );
}

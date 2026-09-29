import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { usePrefersReducedMotion } from '../lib/motion';
import { AssistantAvatar } from './AssistantAvatar';

/** How long each beat of the bubble lasts: typing, then the message, then a pause. */
const TYPING_MS = 1400;
const SHOW_MS = 4200;
const GAP_MS = 900;
/** After going through its lines, the bubble rests this long before starting again. */
const REST_MS = 24000;

type Phase = 'hidden' | 'typing' | 'message';

/**
 * The assistant, as an agent standing by in the bottom-right corner of Home
 * in both apps, above the tab bar - not a card in the list.
 * <p>
 * Its animated face sits in a slowly turning ring with a green "online"
 * dot. Beside it a chat bubble does what a live agent does: shows it
 * typing, then says a line, then the next - and after the last one it
 * goes quiet for a while, so it never nags. A tap anywhere on it opens the
 * assistant. Under reduced motion there is no bubble and nothing turns;
 * the face is its still picture.
 * <p>
 * Rendered into document.body: a page's arrival leaves a transform on its
 * wrapper (see Overlay), which would otherwise make `fixed` scroll away
 * with the page.
 */
export function AssistantFab({ lines, label, onOpen }: { lines: string[]; label: string; onOpen: () => void }) {
  const reduced = usePrefersReducedMotion();
  const [phase, setPhase] = useState<Phase>('hidden');
  const [line, setLine] = useState(0);

  useEffect(() => {
    if (reduced || lines.length === 0) {
      setPhase('hidden');
      return;
    }
    let timer = 0;
    let index = 0;
    const step = (next: Phase, wait: number) => {
      timer = window.setTimeout(() => {
        setPhase(next);
        if (next === 'typing') step('message', TYPING_MS);
        else if (next === 'message') step('hidden', SHOW_MS);
        else {
          index += 1;
          if (index >= lines.length) {
            index = 0;
            setLine(0);
            step('typing', REST_MS);
          } else {
            setLine(index);
            step('typing', GAP_MS);
          }
        }
      }, wait);
    };
    setLine(0);
    step('typing', 1600);
    return () => window.clearTimeout(timer);
  }, [reduced, lines.length]);

  if (typeof document === 'undefined') return null;

  return createPortal(
    <div
      className="pointer-events-none fixed inset-x-0 z-40 mx-auto flex max-w-md justify-end px-screen"
      style={{ bottom: 'calc(5.5rem + env(safe-area-inset-bottom))' }}
    >
      <div className="flex items-end gap-2">
        {phase !== 'hidden' && (
          <button
            type="button"
            onClick={onOpen}
            tabIndex={-1}
            aria-hidden="true"
            // Keyed so each new line pops in afresh.
            key={`${phase}-${line}`}
            className="pointer-events-auto mb-2 max-w-[13.5rem] origin-bottom-right rounded-2xl rounded-br-md bg-surface px-3 py-2 text-left text-caption font-medium leading-snug text-text-primary shadow-float ring-1 ring-border motion-safe:animate-pop-in"
            data-testid="agent-bubble"
          >
            {phase === 'typing' ? (
              <span className="flex h-4 items-center gap-1 px-1" data-testid="agent-typing">
                {[0, 1, 2].map((i) => (
                  <span key={i} className="h-1.5 w-1.5 rounded-full bg-primary motion-safe:animate-bounce" style={{ animationDelay: `${i * 150}ms` }} />
                ))}
              </span>
            ) : (
              lines[line]
            )}
          </button>
        )}

        <button
          type="button"
          onClick={onOpen}
          aria-label={label}
          className="group pointer-events-auto relative flex h-16 w-16 shrink-0 items-center justify-center rounded-full motion-safe:animate-pop-in motion-safe:active:scale-95"
          style={{ animationDelay: '400ms' }}
          data-testid="home-ask-sheout"
        >
          {/* A ring of brand colour turning slowly round the face. */}
          <span
            aria-hidden="true"
            className="absolute inset-0 rounded-full motion-safe:animate-[spin_6s_linear_infinite]"
            style={{ background: 'conic-gradient(from 0deg, #7B3FE4, #EC4899, #F59E0B, #7B3FE4)' }}
          />
          <span aria-hidden="true" className="absolute inset-[3px] rounded-full bg-surface shadow-float" />
          <AssistantAvatar size={48} className="relative" />
          {/* Online. */}
          <span aria-hidden="true" className="absolute bottom-0.5 right-0.5 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-surface">
            <span className="absolute h-2.5 w-2.5 rounded-full bg-accent-green motion-safe:animate-pulse-ring" />
            <span className="relative h-2.5 w-2.5 rounded-full bg-accent-green" />
          </span>
        </button>
      </div>
    </div>,
    document.body
  );
}

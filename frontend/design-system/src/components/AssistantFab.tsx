import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { usePrefersReducedMotion } from '../lib/motion';
import { AssistantAvatar } from './AssistantAvatar';

/** How long each beat of the bubble lasts: typing, then the message, then a pause. */
const TYPING_MS = 1400;
const SHOW_MS = 4200;
const GAP_MS = 900;
/** Set once the greeting has been given: once per app session, never on a loop. */
const GREETED_KEY = 'sheout.agentGreeted';

function alreadyGreeted(): boolean {
  try {
    return sessionStorage.getItem(GREETED_KEY) === '1';
  } catch {
    return false;
  }
}

/** The bubble's place, above the button: wide and tall enough for a two-line greeting. */
const BUBBLE_W = 176;
const BUBBLE_H = 64;

/**
 * Is the bubble's place over empty page, with nothing under it to read or
 * tap? Sampled across the area; at each point the top element must be the
 * page itself (a container as wide as the screen and most of its height)
 * or an empty element, never a card, a line of text, a picture or a button.
 */
function placeIsClear(anchor: HTMLElement | null): boolean {
  if (!anchor) return false;
  const a = anchor.getBoundingClientRect();
  const right = a.right - 8;
  const bottom = a.top - 8;
  for (const fx of [0.05, 0.5, 0.95]) {
    for (const fy of [0.1, 0.5, 0.9]) {
      const x = right - BUBBLE_W * (1 - fx);
      const y = bottom - BUBBLE_H * (1 - fy);
      const under = document.elementsFromPoint(x, y).find((el) => !el.closest('[data-agent]'));
      if (!under) continue;
      const r = under.getBoundingClientRect();
      const isPage = r.width >= window.innerWidth * 0.9 && r.height >= window.innerHeight * 0.6;
      // Or an element with nothing in it - a spacer, the room Home leaves.
      const isEmpty = !under.textContent?.trim() && !under.querySelector('img, svg, video, canvas, button, input, textarea, select')
        && !['IMG', 'SVG', 'VIDEO', 'CANVAS', 'BUTTON', 'INPUT', 'TEXTAREA', 'SELECT'].includes(under.tagName.toUpperCase());
      if (!isPage && !isEmpty) return false;
    }
  }
  return true;
}

function markGreeted() {
  try {
    sessionStorage.setItem(GREETED_KEY, '1');
  } catch {
    // Storage blocked: the greeting simply may come again next visit.
  }
}

type Phase = 'hidden' | 'typing' | 'message';

/**
 * The assistant, as an agent standing by in the bottom-right corner of Home
 * in both apps, above the tab bar - not a card in the list.
 * <p>
 * Its animated face sits in a slowly turning ring with a green "online"
 * dot. Above it a chat bubble greets her: typing, a line, the next - ONCE
 * per app session, and ONLY OVER EMPTY PAGE (placeIsClear). It floats, so
 * it must never stand between her and what she came for: it went round
 * every 24 seconds right across the service cards, and a tap meant for a
 * card opened the assistant. Home is full at first view, so the greeting
 * waits; scrolled to the end, Home leaves room above the button (its
 * agent-clearance spacer), and it greets there. It goes the moment she
 * scrolls again or touches anything else. A tap on it opens the assistant. Under reduced
 * motion there is no bubble and nothing turns; the face is its still
 * picture.
 * <p>
 * Rendered into document.body: a page's arrival leaves a transform on its
 * wrapper (see Overlay), which would otherwise make `fixed` scroll away
 * with the page.
 */
export function AssistantFab({ lines, label, onOpen }: { lines: string[]; label: string; onOpen: () => void }) {
  const reduced = usePrefersReducedMotion();
  const [phase, setPhase] = useState<Phase>('hidden');
  const [line, setLine] = useState(0);

  const agent = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (reduced || lines.length === 0 || alreadyGreeted()) {
      setPhase('hidden');
      return;
    }
    let timer = 0;
    let settle = 0;
    let index = 0;
    let started = false;
    let done = false;
    const stop = () => {
      if (done) return;
      done = true;
      window.clearTimeout(timer);
      window.clearTimeout(settle);
      setPhase('hidden');
      markGreeted();
    };
    const step = (next: Phase, wait: number) => {
      timer = window.setTimeout(() => {
        setPhase(next);
        if (next === 'typing') step('message', TYPING_MS);
        else if (next === 'message') step('hidden', SHOW_MS);
        else {
          index += 1;
          if (index >= lines.length) stop();
          else {
            setLine(index);
            step('typing', GAP_MS);
          }
        }
      }, wait);
    };
    // Starts only when its place is clear; until then it waits, and looks
    // again each time the page comes to rest after a scroll.
    const tryStart = () => {
      if (started || done || !placeIsClear(agent.current)) return;
      started = true;
      setLine(0);
      step('typing', 0);
    };
    // A scroll (the page's own scroller included - hence capture): while it
    // speaks, it goes; before, it looks again once the page is still.
    const onScroll = () => {
      if (started) stop();
      else {
        window.clearTimeout(settle);
        settle = window.setTimeout(tryStart, 600);
      }
    };
    // A touch anywhere but the agent: she is busy.
    const onPointer = (e: PointerEvent) => {
      if (started && !(e.target as Element | null)?.closest?.('[data-agent]')) stop();
    };
    document.addEventListener('scroll', onScroll, { capture: true, passive: true });
    document.addEventListener('pointerdown', onPointer, { capture: true });
    settle = window.setTimeout(tryStart, 1600);
    return () => {
      window.clearTimeout(timer);
      window.clearTimeout(settle);
      document.removeEventListener('scroll', onScroll, { capture: true });
      document.removeEventListener('pointerdown', onPointer, { capture: true });
    };
  }, [reduced, lines.length]);

  if (typeof document === 'undefined') return null;

  return createPortal(
    <div
      className="pointer-events-none fixed inset-x-0 z-40 mx-auto flex max-w-md justify-end px-screen"
      style={{ bottom: 'calc(5.5rem + env(safe-area-inset-bottom))' }}
    >
      {/* Above the button, at the edge - not across the page beside it. */}
      <div className="flex flex-col items-end gap-2" data-agent>
        {phase !== 'hidden' && (
          <button
            type="button"
            onClick={onOpen}
            tabIndex={-1}
            aria-hidden="true"
            // Keyed so each new line pops in afresh.
            key={`${phase}-${line}`}
            className="pointer-events-auto mr-2 max-w-[11rem] origin-bottom-right rounded-2xl rounded-br-md bg-surface px-3 py-2 text-left text-caption font-medium leading-snug text-text-primary shadow-float ring-1 ring-border motion-safe:animate-pop-in"
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
          ref={agent}
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

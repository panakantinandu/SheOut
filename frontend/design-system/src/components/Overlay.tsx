import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { cn } from '../lib/cn';

export interface OverlayProps {
  open: boolean;
  /** Announced as the dialog's name. */
  label: string;
  /** Escape, and a tap on the dark area outside. Omit to make the overlay unclosable. */
  onDismiss?: () => void;
  /** Where the panel sits: a sheet rises from the bottom, a dialog sits in the middle. */
  align?: 'sheet' | 'centre';
  /** Extra classes for the backdrop, e.g. padding around a centred dialog. */
  className?: string;
  children: ReactNode;
}

/**
 * The dark layer everything modal sits on.
 * <p>
 * IT RENDERS INTO document.body, AND THAT IS THE WHOLE POINT. `position:
 * fixed` is only relative to the viewport while no ancestor has a transform,
 * a filter or containment - any one of those makes that ancestor the
 * containing block instead. Every page in the app arrives with a six-pixel
 * slide (see PageTransition), which leaves exactly such a transform on the
 * wrapper, so a "full screen" overlay written the obvious way covered the
 * page's content column and nothing else: the dim stopped short of the
 * header, and the sheet floated in the middle of the page rather than
 * sitting at the bottom of the screen. It looked like a half-finished
 * component and was really a stacking-context bug, in six different places
 * at once. A portal is immune to it, so it is done here once.
 * <p>
 * Escape and a tap outside both dismiss, body scroll is locked while open,
 * and the backdrop is a plain element rather than a button so a tap that
 * lands on it never reads as an action to a screen reader.
 */
export function Overlay({ open, label, onDismiss, align = 'centre', className, children }: OverlayProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onDismiss?.();
    };
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onDismiss]);

  if (!open || typeof document === 'undefined') return null;

  return createPortal(
    <div
      className={cn(
        'fixed inset-0 z-50 flex justify-center bg-[#100A1A]/55 backdrop-blur-[2px] motion-safe:animate-fade-in',
        align === 'sheet' ? 'items-end' : 'items-center',
        className
      )}
      role="dialog"
      aria-modal="true"
      aria-label={label}
      onClick={() => onDismiss?.()}
    >
      {/* The backdrop dismisses; a tap inside the panel must not bubble up
          and dismiss it too. */}
      <div
        className={cn('w-full', align === 'sheet' ? 'max-w-md' : 'flex max-w-md justify-center')}
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>,
    document.body
  );
}

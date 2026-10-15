import { useEffect, useRef, type RefObject } from 'react';

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function focusables(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (el) => !el.closest('[aria-hidden="true"]') && el.getClientRects().length > 0
  );
}

/**
 * Keyboard behaviour every dialog and sheet needs (WCAG 2.1.2, 2.4.3):
 * <ul>
 *   <li>focus moves into it when it opens - to the element already focused
 *       inside it (an autofocused search box), or else its first control;</li>
 *   <li>Tab and Shift+Tab stay inside it - before this, Tab walked out of
 *       the open address picker into the screen underneath;</li>
 *   <li>Escape closes it, when it can be closed;</li>
 *   <li>focus returns to whatever opened it when it closes.</li>
 * </ul>
 */
export function useDialogFocus(ref: RefObject<HTMLElement | null>, open: boolean, onEscape?: () => void) {
  const escape = useRef(onEscape);
  escape.current = onEscape;
  // The last thing focused outside the dialog. Read when it opens: by then an
  // autofocused field inside it may already hold focus, so the element that
  // opened it has to have been noted before.
  const lastOutside = useRef<HTMLElement | null>(null);
  useEffect(() => {
    const note = (e: FocusEvent) => {
      const target = e.target;
      // Not inside this dialog, nor any other modal (the ref may not be set
      // yet when an autofocused field inside it takes focus).
      if (target instanceof HTMLElement && !ref.current?.contains(target) && !target.closest('[aria-modal="true"]')) {
        lastOutside.current = target;
      }
    };
    if (document.activeElement instanceof HTMLElement) lastOutside.current = document.activeElement;
    document.addEventListener('focusin', note);
    return () => document.removeEventListener('focusin', note);
  }, [ref]);

  useEffect(() => {
    if (!open) return;
    const opener = lastOutside.current;
    const root = ref.current;
    if (root && !root.contains(document.activeElement)) {
      const first = focusables(root)[0];
      (first ?? root).focus({ preventScroll: true });
    }
    const onKey = (e: KeyboardEvent) => {
      const container = ref.current;
      if (!container) return;
      if (e.key === 'Escape' && escape.current) {
        e.preventDefault();
        escape.current();
        return;
      }
      if (e.key !== 'Tab') return;
      const items = focusables(container);
      if (items.length === 0) {
        e.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const active = document.activeElement;
      if (e.shiftKey && (active === first || !container.contains(active))) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && (active === last || !container.contains(active))) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      // Back to the button that opened it, if it is still on the page.
      if (opener && opener.isConnected) opener.focus({ preventScroll: true });
    };
  }, [open, ref]);
}

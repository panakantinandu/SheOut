import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

/** The part of the screen she can actually see: under the keyboard, it is only the top of it. */
function useVisibleArea() {
  const read = () => {
    const vv = typeof window !== 'undefined' ? window.visualViewport : null;
    return { top: vv?.offsetTop ?? 0, height: vv?.height ?? (typeof window !== 'undefined' ? window.innerHeight : 0) };
  };
  const [area, setArea] = useState(read);
  useEffect(() => {
    const vv = window.visualViewport;
    const update = () => setArea(read());
    update();
    vv?.addEventListener('resize', update);
    vv?.addEventListener('scroll', update);
    window.addEventListener('resize', update);
    return () => {
      vv?.removeEventListener('resize', update);
      vv?.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, []);
  return area;
}

/**
 * A screen that behaves like a messaging app when the keyboard opens: it
 * fills exactly the part of the screen left above the keyboard, so its
 * header stays at the top, whatever scrolls inside it shrinks, and the box
 * she is typing in sits just above the keys.
 * <p>
 * On iPhone the keyboard does not shrink the page - Safari slides the whole
 * page up behind it instead - so a chat laid out as an ordinary page had
 * its header and conversation pushed off the top, leaving the input
 * floating in the middle of an empty screen. This follows the browser's
 * visual viewport (the visible area) instead, on every phone.
 * <p>
 * Rendered into document.body, like Overlay: a page's arrival animation
 * leaves a transform on its wrapper, and `fixed` inside a transform is not
 * fixed. The page behind is kept from scrolling while it is up.
 */
export function KeyboardAwareScreen({ children, className = '' }: { children: ReactNode; className?: string }) {
  const area = useVisibleArea();

  useEffect(() => {
    const html = document.documentElement;
    const previous = { html: html.style.overflow, body: document.body.style.overflow };
    html.style.overflow = 'hidden';
    document.body.style.overflow = 'hidden';
    return () => {
      html.style.overflow = previous.html;
      document.body.style.overflow = previous.body;
    };
  }, []);

  if (typeof document === 'undefined') return null;
  return createPortal(
    <div
      className={`fixed inset-x-0 z-30 mx-auto flex max-w-md flex-col bg-background px-screen pt-6 ${className}`}
      style={{ top: area.top, height: area.height }}
      data-testid="keyboard-aware-screen"
    >
      {children}
    </div>,
    document.body
  );
}

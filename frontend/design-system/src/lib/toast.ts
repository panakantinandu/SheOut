import { colors, radii, shadows } from '../../tokens.js';

const CONTAINER_ID = 'sheout-toast-container';
const VISIBLE_MS = 3200;

/**
 * A brief, styled message at the bottom of the screen.
 * <p>
 * Replaces window.alert, which both apps used for "this button has nothing
 * behind it yet" feedback. An alert is modal, says "localhost says", cannot
 * be styled, and is silently suppressed entirely in some in-app browsers -
 * which would turn the one signal that a tap registered back into no
 * feedback at all.
 * <p>
 * Deliberately imperative and DOM-level rather than a React component:
 * the callers are plain functions called from event handlers, not
 * components, so a provider and context would mean touching every call
 * site to deliver the same message.
 */
export function showToast(message: string) {
  if (typeof document === 'undefined') return;

  let container = document.getElementById(CONTAINER_ID);
  if (!container) {
    container = document.createElement('div');
    container.id = CONTAINER_ID;
    container.setAttribute('role', 'status');
    container.setAttribute('aria-live', 'polite');
    container.style.cssText = [
      'position:fixed', 'left:50%', 'bottom:96px', 'transform:translateX(-50%)',
      'z-index:60', 'display:flex', 'flex-direction:column', 'gap:8px',
      'width:calc(100% - 32px)', 'max-width:360px', 'pointer-events:none',
    ].join(';');
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.textContent = message;
  toast.style.cssText = [
    `background:${colors.textPrimary}`, `color:${colors.textInverse}`,
    `border-radius:${radii.input}`, `box-shadow:${shadows.card}`,
    'padding:12px 16px', 'font-size:13px', 'line-height:1.4',
    'opacity:0', 'transition:opacity 180ms ease, transform 180ms ease',
    'transform:translateY(6px)',
  ].join(';');
  container.appendChild(toast);

  // next frame, so the transition actually runs rather than being skipped
  requestAnimationFrame(() => {
    toast.style.opacity = '1';
    toast.style.transform = 'translateY(0)';
  });

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(6px)';
    setTimeout(() => {
      toast.remove();
      if (container && container.childElementCount === 0) container.remove();
    }, 200);
  }, VISIBLE_MS);
}

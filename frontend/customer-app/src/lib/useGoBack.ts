import { useCallback, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 * Whether there is a page of this app behind the current one. React Router
 * numbers its history entries (`idx`); 0 means this page is the first -
 * opened from a notification, a link, a refresh, or reached by a redirect
 * that replaced the page before it (Splash to Home).
 */
export function hasAppHistory(): boolean {
  return ((window.history.state as { idx?: number } | null)?.idx ?? 0) > 0;
}

/**
 * The header's back arrow: back to the page she came from, and to
 * `fallback` when there is none. A bare navigate(-1) on a first page
 * leaves the app, or does nothing, instead of going anywhere useful.
 */
export function useGoBack(fallback = '/home') {
  const navigate = useNavigate();
  return useCallback(() => {
    if (hasAppHistory()) navigate(-1);
    else navigate(fallback, { replace: true });
  }, [navigate, fallback]);
}

/**
 * A sheet or full-screen picker that the phone's back button closes.
 * <p>
 * A sheet is not a page, so without this, Back on Android (or the browser's
 * back) went past it and off the screen underneath - out of the booking
 * she was in the middle of. While `open`, the sheet holds one history entry
 * of its own (the same address, marked in its state): Back pops that entry
 * and the sheet closes; closing it any other way pops the entry for her, so
 * the history is as it was before the sheet opened.
 */
export function useCloseOnBack(open: boolean, onClose: () => void) {
  const navigate = useNavigate();
  const location = useLocation();
  const holding = useRef(false);
  const close = useRef(onClose);
  close.current = onClose;
  const marked = (location.state as { sheet?: boolean } | null)?.sheet === true;

  useEffect(() => {
    if (open && !holding.current) {
      holding.current = true;
      const state = typeof location.state === 'object' && location.state !== null ? location.state : {};
      navigate(`${location.pathname}${location.search}${location.hash}`, { state: { ...state, sheet: true } });
    } else if (!open && holding.current) {
      holding.current = false;
      if (marked) navigate(-1);
    }
    // Only the sheet opening or closing moves history.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Back was pressed: the entry is gone, so is the sheet.
  useEffect(() => {
    if (holding.current && !marked) {
      holding.current = false;
      close.current();
    }
  }, [marked, location.key]);
}

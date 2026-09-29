import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

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

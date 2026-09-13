import { Navigation } from 'lucide-react';
import { Button } from './Button';

export interface OpenInMapsButtonProps {
  lat: number;
  lng: number;
  /** What the destination is called, for the pin's label in Google Maps. */
  label?: string;
  /** Defaults to "Open in Google Maps". Give the phase a name where one helps. */
  children?: string;
  variant?: 'primary' | 'secondary';
  fullWidth?: boolean;
  className?: string;
}

/**
 * The universal Google Maps directions URL.
 * <p>
 * This is the officially documented cross-platform form, and it is the
 * FALLBACK here rather than the only answer because on a desktop browser it
 * is also the right answer. On Android and iOS it opens the Google Maps app
 * when installed and the web map when not.
 */
function universalUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`;
}

/**
 * Sends a partner to real turn-by-turn navigation, on whatever she is
 * holding.
 * <p>
 * The in-app Leaflet map shows her where the pickup is relative to her. It
 * is not, and should not try to be, navigation: no voice, no lane guidance,
 * no live traffic, no rerouting when she misses a turn. Every real driver
 * app hands that job to Google Maps, and so does this.
 * <p>
 * THREE PATHS, BECAUSE THE PLATFORMS GENUINELY DIFFER:
 * <ul>
 *   <li><b>Android</b> gets an {@code intent://} URL carrying
 *       {@code S.browser_fallback_url}. Chrome opens the Google Maps app if
 *       it is installed and navigates to the fallback URL itself if it is
 *       not. The fallback is handled by the browser, so there is no timer
 *       and no guessing.</li>
 *   <li><b>iOS</b> gets {@code comgooglemaps://}, which is Google Maps' own
 *       scheme. iOS offers no way to ask whether an app is installed, so
 *       this is the one case that needs the timer trick below.</li>
 *   <li><b>Everything else</b> - desktop, and any browser not matched above
 *       - goes straight to the universal https URL, which always works.</li>
 * </ul>
 * <p>
 * THE iOS TIMER IS A HEURISTIC AND IS WRITTEN AS ONE. If the app opens,
 * Safari is backgrounded and the page is hidden, so the fallback is skipped.
 * If nothing handles the scheme the page is still visible when the timer
 * fires and the universal URL takes over. The visibility check is what keeps
 * it from throwing a second navigation at somebody who has already left, and
 * it is why this is not simply "navigate twice and hope".
 */
export function OpenInMapsButton({
  lat,
  lng,
  label,
  children = 'Open in Google Maps',
  variant = 'secondary',
  fullWidth = true,
  className,
}: OpenInMapsButtonProps) {
  function open() {
    const fallback = universalUrl(lat, lng);
    const ua = typeof navigator === 'undefined' ? '' : navigator.userAgent;

    if (/android/i.test(ua)) {
      const destination = label ? `${lat},${lng}(${label})` : `${lat},${lng}`;
      window.location.href =
        `intent://maps.google.com/maps?daddr=${encodeURIComponent(destination)}&mode=d`
        + `#Intent;scheme=https;package=com.google.android.apps.maps;`
        + `S.browser_fallback_url=${encodeURIComponent(fallback)};end`;
      return;
    }

    // iPadOS reports itself as a Mac, so the touch check is what
    // distinguishes a tablet - where the Google Maps app may well be
    // installed - from a desktop, where it cannot be.
    const isIos =
      /iphone|ipad|ipod/i.test(ua)
      || (/macintosh/i.test(ua) && typeof document !== 'undefined' && 'ontouchend' in document);

    if (isIos) {
      const timer = window.setTimeout(() => {
        // Still here, so nothing took the scheme.
        if (!document.hidden) window.location.href = fallback;
      }, 1200);
      // If the app did open, the page is hidden and the fallback must not
      // fire - otherwise she comes back from navigating to find the browser
      // has loaded a map over the top of what she was doing.
      const cancel = () => {
        if (document.hidden) window.clearTimeout(timer);
      };
      document.addEventListener('visibilitychange', cancel, { once: true });
      window.location.href = `comgooglemaps://?daddr=${lat},${lng}&directionsmode=driving`;
      return;
    }

    // Desktop and anything unrecognised. A new tab rather than replacing
    // this one: on a driver's screen mid-trip, navigating away from the trip
    // screen is the last thing to do.
    window.open(fallback, '_blank', 'noopener,noreferrer');
  }

  return (
    <Button
      variant={variant}
      fullWidth={fullWidth}
      className={className}
      icon={<Navigation className="h-4 w-4" />}
      onClick={open}
    >
      {children}
    </Button>
  );
}

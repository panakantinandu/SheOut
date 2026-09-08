import { Bike, Package, UtensilsCrossed } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { IconCircle } from '@sheout/design-system';
import { useAuth } from '../auth/AuthContext';

const FADE_START_MS = 1700;
const NAVIGATE_MS = 2000;

const SERVICES = [
  { key: 'bike', label: 'Bike Taxi', icon: <Bike /> },
  { key: 'parcel', label: 'Parcel Delivery', icon: <Package /> },
  { key: 'lunch', label: 'Lunch Box Delivery', icon: <UtensilsCrossed /> },
];

/**
 * REBUILT to match the mockup's left panel, not just tweaked - the
 * previous version already routed "/" -> Splash -> /login correctly (that
 * part was never actually broken), but visually it was a plain centered
 * logo/text block with no wave, no skyline, and colored (not white)
 * footer icons - close enough to blend into the background that it read
 * as "missing" rather than as its own distinct screen.
 * <p>
 * Auto-navigates with a brief fade (2s total: content sits still, then
 * fades over the last ~300ms) rather than a tap-to-continue "Get
 * Started" button - flagged per your ask: a manual button reads as more
 * intentional/brand-forward (SheOUT trial prototype used one), but
 * auto-advance is the more common pattern for a splash whose only job is
 * routing to Login, and avoids an extra tap before every fresh app
 * open - going with auto-advance as instructed, but this is a genuine
 * toss-up, not a strong recommendation either way.
 * <p>
 * FLAGGED - APPROXIMATED, NOT MEASURED: the wave's exact curve control
 * points and the skyline's building widths/heights/spacing are eyeballed
 * to read as "an organic wave" / "a faint skyline," not traced pixel-for-
 * pixel from the mockup - worth a side-by-side check once live.
 */
export function Splash() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [fading, setFading] = useState(false);

  useEffect(() => {
    const fadeTimer = setTimeout(() => setFading(true), FADE_START_MS);
    const navTimer = setTimeout(() => {
      navigate(isAuthenticated ? '/home' : '/login', { replace: true });
    }, NAVIGATE_MS);
    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(navTimer);
    };
  }, [navigate, isAuthenticated]);

  return (
    <div
      className={`relative min-h-screen overflow-hidden bg-background transition-opacity duration-300 ${
        fading ? 'opacity-0' : 'opacity-100'
      }`}
    >
      {/* Faint skyline silhouette, sitting just above the wave - approximated shapes, see file header. */}
      <svg
        viewBox="0 0 400 100"
        preserveAspectRatio="none"
        className="absolute inset-x-0 bottom-[34%] h-24 w-full text-primary/10"
        aria-hidden="true"
      >
        <rect x="-10" y="45" width="34" height="55" fill="currentColor" />
        <rect x="26" y="20" width="26" height="80" fill="currentColor" />
        <rect x="56" y="55" width="22" height="45" fill="currentColor" />
        <rect x="82" y="35" width="30" height="65" fill="currentColor" />
        <rect x="116" y="60" width="20" height="40" fill="currentColor" />
        <rect x="140" y="15" width="28" height="85" fill="currentColor" />
        <rect x="172" y="48" width="24" height="52" fill="currentColor" />
        <rect x="200" y="30" width="32" height="70" fill="currentColor" />
        <rect x="236" y="58" width="20" height="42" fill="currentColor" />
        <rect x="260" y="22" width="26" height="78" fill="currentColor" />
        <rect x="290" y="50" width="24" height="50" fill="currentColor" />
        <rect x="318" y="38" width="30" height="62" fill="currentColor" />
        <rect x="352" y="58" width="22" height="42" fill="currentColor" />
        <rect x="380" y="25" width="30" height="75" fill="currentColor" />
      </svg>

      {/* Purple wave footer - an approximated organic curve, not traced from the mockup. */}
      <svg
        viewBox="0 0 400 300"
        preserveAspectRatio="none"
        className="absolute inset-x-0 bottom-0 h-[46%] w-full"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id="splashWave" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#7B3FE4" />
            <stop offset="100%" stopColor="#5A2DA0" />
          </linearGradient>
        </defs>
        <path
          d="M0,70 C70,15 150,100 230,55 C300,18 350,75 400,45 L400,300 L0,300 Z"
          fill="url(#splashWave)"
        />
      </svg>

      <div className="relative z-10 flex min-h-screen flex-col items-center px-screen py-10">
        <div className="flex flex-1 flex-col items-center justify-center gap-3">
          <div className="relative flex items-center justify-center">
            <span className="absolute h-44 w-44 rounded-full border-8 border-accent-orange/15" aria-hidden="true" />
            <img
              src="/Logo.jpeg"
              alt="SheOut"
              className="relative h-36 w-36 rounded-card object-cover shadow-card"
            />
          </div>

          <p className="font-heading text-3xl font-extrabold tracking-tight text-text-primary">
            SHE<span className="text-accent-orange">O</span>UT
          </p>

          <div className="flex items-center gap-2">
            <span className="h-px w-6 bg-accent-orange" aria-hidden="true" />
            <p className="text-xs font-semibold uppercase tracking-wide text-accent-orange">
              Your Delivery, Our Priority
            </p>
            <span className="h-px w-6 bg-accent-orange" aria-hidden="true" />
          </div>
        </div>

        <div className="flex w-full flex-col items-center gap-5 pb-4">
          <div className="flex w-full justify-around">
            {SERVICES.map((service) => (
              <div key={service.key} className="flex flex-col items-center gap-2">
                <IconCircle icon={service.icon} />
                <span className="text-xs font-medium text-text-inverse">{service.label}</span>
              </div>
            ))}
          </div>

          <p className="text-xs font-medium uppercase tracking-wide text-text-inverse/70">
            Safe &middot; Fast &middot; Women Focused
          </p>
        </div>
      </div>
    </div>
  );
}

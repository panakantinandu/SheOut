import { useLocation, useNavigate } from 'react-router-dom';
import { BrandSplash, SPLASH_DISPLAY_MS, SPLASH_FADE_MS, splashDestination, useSplashTimer, useTranslation, ServiceArt } from '@sheout/design-system';
import { useAuth } from '../auth/AuthContext';

/** Held for the same time in both apps - see useSplashTimer in the design system. */
const FADE_START_MS = SPLASH_DISPLAY_MS - SPLASH_FADE_MS;

/**
 * The first screen on every launch - including every cold launch from the
 * home-screen icon, which used to skip it (see coldStart.ts in the design
 * system). Moves on to Login, or to Home - or wherever the launch was headed,
 * such as a notification's trip - once it has had its moment.
 * <p>
 * Lunch Box Delivery is deferred for launch, so it is not advertised here;
 * it comes back alongside the Home tile.
 */
export function Splash() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated } = useAuth();
  // Timed once, and once per launch: a reload mid-splash (an update) goes straight on.
  const { fading, skipped } = useSplashTimer(() =>
    navigate(splashDestination(location.search, isAuthenticated), { replace: true })
  );
  if (skipped) return null;

  return (
    <BrandSplash
      // The bar fills over exactly as long as this screen is held, so it
      // reaches the end as the app opens rather than at some other moment.
      durationMs={FADE_START_MS}
      fading={fading}
      items={[
        { key: 'bike', label: t('home.serviceRide'), icon: <ServiceArt kind="ride" size="lg" className="rounded-2xl ring-2 ring-white/40" /> },
        { key: 'parcel', label: t('home.serviceParcel'), icon: <ServiceArt kind="parcel" size="lg" className="rounded-2xl ring-2 ring-white/40" /> },
      ]}
      footerLine={t('splash.footer')}
    />
  );
}

import { Bike, Package } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandSplash, splashDestination, useTranslation } from '@sheout/design-system';
import { useAuth } from '../auth/AuthContext';

/** How long the splash is shown before it moves on. */
const SPLASH_DISPLAY_MS = 3500;

/**
 * Matches the CSS transition in BrandSplash, and is derived from
 * SPLASH_DISPLAY_MS so the fade always lands exactly as the screen moves on.
 */
const FADE_DURATION_MS = 300;
const FADE_START_MS = SPLASH_DISPLAY_MS - FADE_DURATION_MS;

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
  const [fading, setFading] = useState(false);

  useEffect(() => {
    const fadeTimer = setTimeout(() => setFading(true), FADE_START_MS);
    const navTimer = setTimeout(() => {
      navigate(splashDestination(location.search, isAuthenticated), { replace: true });
    }, SPLASH_DISPLAY_MS);
    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(navTimer);
    };
  }, [navigate, isAuthenticated, location.search]);

  return (
    <BrandSplash
      fading={fading}
      items={[
        { key: 'bike', label: t('home.serviceRide'), icon: <Bike /> },
        { key: 'parcel', label: t('home.serviceParcel'), icon: <Package /> },
      ]}
      footerLine={t('splash.footer')}
    />
  );
}

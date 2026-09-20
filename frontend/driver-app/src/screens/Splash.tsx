import { IndianRupee, Package, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandSplash, splashDestination, useTranslation } from '@sheout/design-system';
import { useAuth } from '../auth/AuthContext';

/**
 * Shorter than the rider app's: a partner opening the app wants to get
 * online, and has seen this screen many times. Still long enough to read as
 * a launch rather than a flicker.
 */
const SPLASH_DISPLAY_MS = 2200;
const FADE_DURATION_MS = 300;
const FADE_START_MS = SPLASH_DISPLAY_MS - FADE_DURATION_MS;

/**
 * The partner app's first screen, on every launch - including from the
 * home-screen icon, which used to skip it (see coldStart.ts). The same brand
 * treatment as the rider app; it used to be a logo JPEG on a plain page.
 * Moves on to Login, Home, or wherever the launch was headed - an offer
 * notification, say.
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
      // The bar fills over exactly as long as this screen is held, so it
      // reaches the end as the app opens rather than at some other moment.
      durationMs={FADE_START_MS}
      fading={fading}
      badge={t('splash.badge')}
      items={[
        { key: 'earn', label: t('splash.earn'), icon: <IndianRupee /> },
        { key: 'deliver', label: t('splash.deliver'), icon: <Package /> },
        { key: 'safe', label: t('splash.safe'), icon: <ShieldCheck /> },
      ]}
      footerLine={t('splash.footer')}
    />
  );
}

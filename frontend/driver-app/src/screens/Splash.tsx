import { IndianRupee, Package, ShieldCheck } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandSplash, SPLASH_DISPLAY_MS, SPLASH_FADE_MS, splashDestination, useSplashTimer, useTranslation } from '@sheout/design-system';
import { useAuth } from '../auth/AuthContext';

/** Held for the same time in both apps - see useSplashTimer in the design system. */
const FADE_START_MS = SPLASH_DISPLAY_MS - SPLASH_FADE_MS;

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

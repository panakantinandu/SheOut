import { MapPin } from 'lucide-react';
import { Button, Overlay, useTranslation } from '@sheout/design-system';
import { devicePlatform } from '../../lib/driverStatus';

/**
 * How to turn location back on, in a few plain steps for her kind of phone.
 * <p>
 * Deliberately generic. Settings menus differ by phone maker, Android
 * version and browser, and a confidently wrong path ("Settings > Apps >
 * Chrome > Permissions") sends her hunting for a menu her phone does not
 * have. So the steps name what to find, not where it is.
 * <p>
 * "Ask again" asks the browser for a position, which shows the permission
 * prompt again where the browser still allows that. Either way, the Home
 * card recovers by itself the moment access is granted; she does not need
 * to reload or come back through this sheet.
 */
export function LocationHelpSheet({ open, onClose, onAskAgain }: { open: boolean; onClose: () => void; onAskAgain: () => void }) {
  const { t } = useTranslation();
  const platform = devicePlatform();
  const steps = [1, 2, 3, 4].map((n) => t(`home.locationHelp.${platform}.step${n}`));

  return (
    <Overlay open={open} label={t('home.locationHelp.title')} onDismiss={onClose} align="sheet">
      <div className="w-full max-w-md space-y-4 rounded-t-[1.75rem] bg-surface px-5 pb-8 pt-5 shadow-overlay motion-safe:animate-sheet-up" data-testid="location-help">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-accent-orange-tint text-accent-orange-strong">
            <MapPin className="h-5 w-5" aria-hidden="true" />
          </span>
          <p className="font-heading text-section text-text-primary">{t('home.locationHelp.title')}</p>
        </div>
        <p className="text-sm text-text-secondary">{t('home.locationHelp.intro')}</p>
        <ol className="space-y-3">
          {steps.map((step, i) => (
            <li key={i} className="flex items-start gap-3 text-sm text-text-primary">
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-light font-semibold text-primary">{i + 1}</span>
              <span className="pt-1">{step}</span>
            </li>
          ))}
        </ol>
        <div className="space-y-2">
          <Button fullWidth size="lg" onClick={onAskAgain} data-testid="location-ask-again">
            {t('home.locationHelp.askAgain')}
          </Button>
          <Button fullWidth size="lg" variant="secondary" onClick={onClose}>
            {t('home.locationHelp.close')}
          </Button>
        </div>
      </div>
    </Overlay>
  );
}

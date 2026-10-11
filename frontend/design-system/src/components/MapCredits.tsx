import { ExternalLink, Map } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Card } from './Card';
import { IconCircle } from './IconCircle';

/**
 * Who the maps and addresses come from, in full - on About, where ride apps
 * keep their legal credits.
 * <p>
 * This is the long form, not a replacement: Google's logo and its "Map data
 * / Terms" line stay on every map, and "Powered by Google" under address
 * search, because Google's terms forbid hiding them (doing so risks the key
 * the maps run on). OpenStreetMap's credit stays where its data is shown.
 */
export function MapCredits() {
  const { t } = useTranslation('ds');
  const link = (href: string, label: string) => (
    <a href={href} target="_blank" rel="noopener noreferrer" className="tap-target gap-1 font-medium text-primary">
      {label} <ExternalLink className="h-3 w-3" aria-hidden="true" />
    </a>
  );
  return (
    <Card className="space-y-3" data-testid="map-credits">
      <div className="flex items-center gap-3">
        <IconCircle size="sm" tone="soft" icon={<Map />} />
        <p className="font-medium text-text-primary">{t('credits.title')}</p>
      </div>
      <div className="space-y-2 text-sm text-text-secondary">
        <p>{t('credits.google')}</p>
        <p className="flex flex-wrap gap-x-4 gap-y-1">
          {link('https://maps.google.com/help/terms_maps/', t('credits.googleTerms'))}
          {link('https://policies.google.com/privacy', t('credits.googlePrivacy'))}
        </p>
        <p>{t('credits.osm')}</p>
        <p>{link('https://www.openstreetmap.org/copyright', t('credits.osmLink'))}</p>
      </div>
    </Card>
  );
}

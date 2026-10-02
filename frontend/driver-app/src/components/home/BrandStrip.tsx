import { BrandStrip as SharedBrandStrip, useTranslation } from '@sheout/design-system';

/** SheOut Partner, by name, at the top of Home - see the design system's BrandStrip. */
export function BrandStrip() {
  const { t } = useTranslation();
  return <SharedBrandStrip app="partner" label={t('home.brandPartner')} />;
}

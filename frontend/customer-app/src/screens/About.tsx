import { Heart, Shield, Users } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, TopHeader, contentText, useAppLanguage, useContentSection } from '@sheout/design-system';
import { contentApi } from '../api/client';
import { useTranslation } from '@sheout/design-system';

const APP_VERSION = '0.1.0';

/**
 * Says what this app actually is and does today rather than marketing copy -
 * every claim below maps to something really built (verification gating, SOS
 * with emergency contacts, per-trip payment). The wording is editable copy
 * from the content module (about.*); the strings here are the seeded
 * fallback. Whoever edits it should keep that rule: no claim the product does
 * not back.
 */
export function About() {
  const { t } = useTranslation();
  const lng = useAppLanguage();
  // Operators edit the English in the console; other languages use the
  // translated defaults until the content module has per-language copy.
  const text = (key: string, fallbackKey: string) => (lng === 'en' ? contentText(copy, key, t(fallbackKey)) : t(fallbackKey));
  const navigate = useNavigate();
  const copy = useContentSection('about.', contentApi.getSection);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('about.title')} onBack={() => navigate(-1)} />

      <Card className="text-center">
        <IconCircle size="lg" tone="soft" icon={<Heart />} className="mx-auto" />
        <p className="mt-3 font-heading text-lg font-semibold text-text-primary">SheOut</p>
        <p className="text-sm text-text-secondary">{t('about.version', { version: APP_VERSION })}</p>
        <p className="mt-3 whitespace-pre-line text-sm text-text-secondary">
          {text('about.body', 'about.body')}
        </p>
      </Card>

      <Card className="space-y-4">
        <div className="flex items-start gap-3">
          <IconCircle size="sm" tone="soft" icon={<Shield />} />
          <div>
            <p className="font-medium text-text-primary">{text('about.feature.1.title', 'about.feature1Title')}</p>
            <p className="whitespace-pre-line text-sm text-text-secondary">
              {text('about.feature.1.body', 'about.feature1Body')}
            </p>
          </div>
        </div>
        <div className="flex items-start gap-3">
          <IconCircle size="sm" tone="soft" icon={<Users />} />
          <div>
            <p className="font-medium text-text-primary">{text('about.feature.2.title', 'about.feature2Title')}</p>
            <p className="whitespace-pre-line text-sm text-text-secondary">
              {text('about.feature.2.body', 'about.feature2Body')}
            </p>
          </div>
        </div>
      </Card>

      <p className="text-center text-xs text-text-secondary">&copy; {new Date().getFullYear()} SheOut</p>
    </div>
  );
}

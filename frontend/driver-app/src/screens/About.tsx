import { HeartHandshake, IndianRupee, ShieldCheck } from 'lucide-react';
import { useGoBack } from '../lib/useGoBack';
import { Card, IconCircle, SheOutWordmark, TopHeader, useTranslation } from '@sheout/design-system';

const APP_VERSION = '0.1.0';

/** About SheOut, told to a partner - what the platform promises her. */
export function About() {
  const { t } = useTranslation();
  const goBack = useGoBack('/home');
  const points = [
    { key: 'safety', icon: <ShieldCheck />, color: 'primary' as const },
    { key: 'earnings', icon: <IndianRupee />, color: 'green' as const },
    { key: 'support', icon: <HeartHandshake />, color: 'orange' as const },
  ];

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('about.title')} onBack={goBack} />

      <Card variant="primary" className="relative overflow-hidden">
        <div className="relative z-10">
          <SheOutWordmark tone="white" className="w-36" />
          <p className="mt-1 text-sm opacity-90">{t('about.tagline')}</p>
          <p className="mt-2 text-xs opacity-80">{t('about.version', { version: APP_VERSION })}</p>
        </div>
      </Card>

      <Card className="space-y-4">
        {points.map((point) => (
          <div key={point.key} className="flex items-start gap-3">
            <IconCircle size="sm" tone="soft" color={point.color} icon={point.icon} />
            <div>
              <p className="font-medium text-text-primary">{t(`about.points.${point.key}.title`)}</p>
              <p className="text-sm text-text-secondary">{t(`about.points.${point.key}.body`)}</p>
            </div>
          </div>
        ))}
      </Card>

      <p className="text-center text-xs text-text-secondary">&copy; {new Date().getFullYear()} SheOut</p>
    </div>
  );
}

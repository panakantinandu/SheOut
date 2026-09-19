import { HeartHandshake, IndianRupee, ShieldCheck } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, TopHeader, brandIllustration, useTranslation } from '@sheout/design-system';

const APP_VERSION = '0.1.0';

/** About SheOut, told to a partner - what the platform promises her. */
export function About() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const points = [
    { key: 'safety', icon: <ShieldCheck />, color: 'primary' as const },
    { key: 'earnings', icon: <IndianRupee />, color: 'green' as const },
    { key: 'support', icon: <HeartHandshake />, color: 'orange' as const },
  ];

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('about.title')} onBack={() => navigate(-1)} />

      <Card variant="primary" className="relative overflow-hidden">
        <div className="relative z-10 max-w-[62%]">
          <p className="font-heading text-xl font-bold">SheOut</p>
          <p className="mt-1 text-sm opacity-90">{t('about.tagline')}</p>
          <p className="mt-2 text-xs opacity-80">{t('about.version', { version: APP_VERSION })}</p>
        </div>
        <img
          src={brandIllustration}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-3 -right-3 h-28 w-28 object-contain opacity-95"
        />
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

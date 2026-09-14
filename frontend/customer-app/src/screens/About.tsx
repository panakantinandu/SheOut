import { Heart, Shield, Users } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, TopHeader, contentText, useContentSection } from '@sheout/design-system';
import { contentApi } from '../api/client';

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
  const navigate = useNavigate();
  const copy = useContentSection('about.', contentApi.getSection);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="About SheOut" onBack={() => navigate('/profile')} />

      <Card className="text-center">
        <IconCircle size="lg" tone="soft" icon={<Heart />} className="mx-auto" />
        <p className="mt-3 font-heading text-lg font-semibold text-text-primary">SheOut</p>
        <p className="text-sm text-text-secondary">Version {APP_VERSION}</p>
        <p className="mt-3 whitespace-pre-line text-sm text-text-secondary">
          {contentText(copy, 'about.body', "Rides and deliveries built around women's safety - women riders, women drivers.")}
        </p>
      </Card>

      <Card className="space-y-4">
        <div className="flex items-start gap-3">
          <IconCircle size="sm" tone="soft" icon={<Shield />} />
          <div>
            <p className="font-medium text-text-primary">{contentText(copy, 'about.feature.1.title', 'Verified drivers')}</p>
            <p className="whitespace-pre-line text-sm text-text-secondary">
              {contentText(copy, 'about.feature.1.body', 'Every driver passes an ID and police-verification review before they can accept a trip.')}
            </p>
          </div>
        </div>
        <div className="flex items-start gap-3">
          <IconCircle size="sm" tone="soft" icon={<Users />} />
          <div>
            <p className="font-medium text-text-primary">{contentText(copy, 'about.feature.2.title', 'Emergency contacts')}</p>
            <p className="whitespace-pre-line text-sm text-text-secondary">
              {contentText(copy, 'about.feature.2.body', 'Add contacts to your account and one tap on SOS sends them your location by SMS.')}
            </p>
          </div>
        </div>
      </Card>

      <p className="text-center text-xs text-text-secondary">&copy; {new Date().getFullYear()} SheOut</p>
    </div>
  );
}

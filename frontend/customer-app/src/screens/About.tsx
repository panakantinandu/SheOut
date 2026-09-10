import { Heart, Shield, Users } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, TopHeader } from '@sheout/design-system';

const APP_VERSION = '0.1.0';

/**
 * Static content screen, replacing a placeholder. Deliberately says what
 * this app actually is and does today rather than marketing copy - every
 * claim below maps to something really built (verification gating, SOS with
 * emergency contacts, per-trip payment).
 */
export function About() {
  const navigate = useNavigate();

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="About SheOut" onBack={() => navigate('/profile')} />

      <Card className="text-center">
        <IconCircle size="lg" tone="soft" icon={<Heart />} className="mx-auto" />
        <p className="mt-3 font-heading text-lg font-semibold text-text-primary">SheOut</p>
        <p className="text-sm text-text-secondary">Version {APP_VERSION}</p>
        <p className="mt-3 text-sm text-text-secondary">
          Rides and deliveries built around women&apos;s safety - women riders, women drivers.
        </p>
      </Card>

      <Card className="space-y-4">
        <div className="flex items-start gap-3">
          <IconCircle size="sm" tone="soft" icon={<Shield />} />
          <div>
            <p className="font-medium text-text-primary">Verified drivers</p>
            <p className="text-sm text-text-secondary">
              Every driver passes an ID and police-verification review before they can accept a trip.
            </p>
          </div>
        </div>
        <div className="flex items-start gap-3">
          <IconCircle size="sm" tone="soft" icon={<Users />} />
          <div>
            <p className="font-medium text-text-primary">Emergency contacts</p>
            <p className="text-sm text-text-secondary">
              Add contacts to your account and one tap on SOS sends them your location by SMS.
            </p>
          </div>
        </div>
      </Card>

      <p className="text-center text-xs text-text-secondary">&copy; {new Date().getFullYear()} SheOut</p>
    </div>
  );
}

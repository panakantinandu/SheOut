import { CheckCircle2, MapPin, Phone, ShieldAlert, Users } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { mockAction } from '../lib/mockAction';

const SAFETY_FEATURES = [
  'Live Location Sharing',
  '24/7 Support',
  'Verified Women Partners',
  'Emergency Contacts',
];

/**
 * Entirely mock/static - the notifications module (which would actually
 * deliver an SOS alert to emergency contacts/support) doesn't exist yet.
 * Every action here is a clearly-labeled TODO, not wired to anything real.
 */
export function Sos() {
  const navigate = useNavigate();

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="SOS" onBack={() => navigate('/home')} />

      <Card className="flex flex-col items-center gap-3 text-center">
        <IconCircle size="lg" color="red" icon={<ShieldAlert />} />
        <p className="font-heading text-lg font-semibold text-text-primary">In Emergency?</p>
        <p className="text-sm text-text-secondary">Press SOS for immediate help</p>
        <Button variant="danger" fullWidth onClick={() => mockAction('SOS alert', 'no notifications module on the backend yet')}>
          Send SOS Alert
        </Button>
      </Card>

      <Card className="divide-y divide-border p-0">
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" icon={<MapPin />} />}
            label="Share Location"
            onClick={() => mockAction('Share Location')}
          />
        </div>
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" color="orange" icon={<Phone />} />}
            label="Call Emergency"
            onClick={() => mockAction('Call Emergency')}
          />
        </div>
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" color="green" icon={<Users />} />}
            label="Contact"
            onClick={() => mockAction('Contact emergency contacts')}
          />
        </div>
      </Card>

      <div>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Safety Features</h2>
        <Card className="space-y-3">
          {SAFETY_FEATURES.map((feature) => (
            <ListRow key={feature} icon={<IconCircle tone="soft" color="green" size="sm" icon={<CheckCircle2 />} />} label={feature} chevron={false} />
          ))}
        </Card>
      </div>
    </div>
  );
}

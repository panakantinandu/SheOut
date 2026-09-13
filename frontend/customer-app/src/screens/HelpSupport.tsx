import { LifeBuoy, Mail, Phone, Siren } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { supportApi } from '../api/client';

const SUPPORT_EMAIL = 'support@sheout.app';

/**
 * Static by design. There is no ticketing or chat backend, so rather than a
 * contact form that posts nowhere, this hands over real contact routes the
 * device can actually act on (mailto:/tel:) plus answers to the questions
 * this app's own behaviour raises. Replaces a placeholder that just said
 * "no support screen built yet".
 * <p>
 * The SOS row routes to the real SOS screen rather than describing it -
 * that is the one genuinely urgent path on this screen.
 * <p>
 * The phone number comes from the backend, not from a constant here. It
 * used to be hardcoded in this file and hardcoded differently in the
 * partner app, so support answered on one number from one app and another
 * from the other, and fixing either meant a frontend deploy. The row is
 * hidden outright when none is configured rather than offering a dead dial.
 */
export function HelpSupport() {
  const navigate = useNavigate();
  const [supportPhone, setSupportPhone] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    supportApi
      .getContact()
      .then((contact) => {
        if (!cancelled) setSupportPhone(contact.phoneNumber);
      })
      .catch(() => {
        // Email and SOS both still work, so a failure here costs one row
        // rather than the screen. Nothing urgent depends on it.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Help & Support" onBack={() => navigate('/profile')} />

      <Card className="text-center">
        <IconCircle size="lg" tone="soft" icon={<LifeBuoy />} className="mx-auto" />
        <p className="mt-3 font-heading font-semibold text-text-primary">We are here to help</p>
        <p className="mt-1 text-sm text-text-secondary">Reach us any time - we usually reply within a day.</p>
      </Card>

      <Card className="divide-y divide-border p-0">
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<Mail />} />}
          label="Email us"
          sublabel={SUPPORT_EMAIL}
          onClick={() => { window.location.href = `mailto:${SUPPORT_EMAIL}`; }}
        />
        {supportPhone && (
          <ListRow
            icon={<IconCircle tone="soft" size="sm" icon={<Phone />} />}
            label="Call support"
            sublabel={supportPhone}
            onClick={() => { window.location.href = `tel:${supportPhone}`; }}
          />
        )}
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<Siren />} />}
          label="Emergency SOS"
          sublabel="Alert your emergency contacts now"
          onClick={() => navigate('/sos')}
        />
      </Card>

      <Card className="space-y-4">
        <div>
          <p className="font-medium text-text-primary">How do I cancel a booking?</p>
          <p className="mt-1 text-sm text-text-secondary">
            Open the trip from Bookings and use Cancel, any time before the trip starts. You will be asked why, in one
            tap. Cancelling often enough is looked at by a person here, never acted on automatically.
          </p>
        </div>
        <div>
          <p className="font-medium text-text-primary">How do I contact my partner?</p>
          <p className="mt-1 text-sm text-text-secondary">
            Through chat on the trip screen, from the moment she accepts until the trip ends. Phone numbers are never
            shared in either direction, and cannot be sent through chat. If something needs a call, call us and we will
            handle it.
          </p>
        </div>
        <div>
          <p className="font-medium text-text-primary">Who can see my SOS alert?</p>
          <p className="mt-1 text-sm text-text-secondary">
            The emergency contacts saved on your account are sent an SMS with your live location, and the alert is
            raised to SheOut operators.
          </p>
        </div>
        <div>
          <p className="font-medium text-text-primary">How am I charged?</p>
          <p className="mt-1 text-sm text-text-secondary">
            Per trip, by UPI or cash. No card is stored on your account - see Payments for your history.
          </p>
        </div>
      </Card>
    </div>
  );
}

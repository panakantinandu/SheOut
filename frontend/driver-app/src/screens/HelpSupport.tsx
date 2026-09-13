import { LifeBuoy, Mail, Phone, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { supportApi } from '../api/client';

const SUPPORT_EMAIL = 'drivers@sheout.app';

/**
 * Static by design - there is no ticketing backend, so this gives real
 * contact routes the device can act on rather than a form that posts
 * nowhere. Replaces a placeholder that said "no support screen built yet".
 * The answers below are driver-specific and describe how this app actually
 * behaves (verification gating, going online, per-trip fares).
 * <p>
 * The phone number comes from the backend, not from a constant here. It
 * used to be hardcoded in this file and hardcoded differently in the rider
 * app, so the two apps reached support on two different numbers and fixing
 * either meant a frontend deploy. The row is hidden outright when none is
 * configured rather than offering a dead dial.
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
        // Email still works, so a failure here costs one row, not the screen.
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
        <p className="mt-3 font-heading font-semibold text-text-primary">Driver support</p>
        <p className="mt-1 text-sm text-text-secondary">We usually reply within a day.</p>
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
            label="Call driver support"
            sublabel={supportPhone}
            onClick={() => { window.location.href = `tel:${supportPhone}`; }}
          />
        )}
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<ShieldCheck />} />}
          label="Verification status"
          sublabel="See what is still outstanding"
          onClick={() => navigate('/verification')}
        />
      </Card>

      <Card className="space-y-4">
        <div>
          <p className="font-medium text-text-primary">Why can I not go online?</p>
          <p className="mt-1 text-sm text-text-secondary">
            Both your ID and police verification must be approved first. Check Verification to see which is still
            pending.
          </p>
        </div>
        <div>
          <p className="font-medium text-text-primary">Why am I not getting requests?</p>
          <p className="mt-1 text-sm text-text-secondary">
            You must be online, verified, and allowing location access - requests are offered to the nearest available
            drivers.
          </p>
        </div>
        <div>
          <p className="font-medium text-text-primary">How do I contact my rider?</p>
          <p className="mt-1 text-sm text-text-secondary">
            Through chat on the trip screen, from the moment you accept until the trip ends. Phone numbers are never
            shared in either direction and cannot be sent through chat. If something needs a call, call us.
          </p>
        </div>
        <div>
          <p className="font-medium text-text-primary">What happens if I cancel a trip?</p>
          <p className="mt-1 text-sm text-text-secondary">
            You will be asked why, in one tap. Cancelling often relative to the trips you take on is reviewed by a
            person here - never acted on automatically - so the reason you give is your side of it.
          </p>
        </div>
        <div>
          <p className="font-medium text-text-primary">How are my earnings worked out?</p>
          <p className="mt-1 text-sm text-text-secondary">
            Earnings are the total of your completed trips. There is no separate payout module yet, so Earnings shows
            trip totals rather than settled payouts.
          </p>
        </div>
      </Card>
    </div>
  );
}

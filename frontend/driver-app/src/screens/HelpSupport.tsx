import { LifeBuoy, Mail, Phone, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, FaqList, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import type { FaqItem } from '@sheout/design-system';
import { supportApi } from '../api/client';

const SUPPORT_EMAIL = 'drivers@sheout.app';

/**
 * Answers written against what this app actually does - verification
 * gating, going online, how a cancellation is treated, how earnings are
 * worked out - rather than generic support copy.
 * <p>
 * Held as data rather than markup so the screen below stays about layout.
 * They render through the shared FaqList, which both apps use, so the two
 * cannot drift into presenting the same kind of information two different
 * ways.
 */
const FAQS: FaqItem[] = [
  {
    question: 'Why can I not go online?',
    answer:
      'Three things have to be in place: your ID and police verification both approved, and a profile photo on your '
      + 'account. Check Verification to see which checks are still pending, and My Profile to add a photo.',
  },
  {
    question: 'Why do I need a profile photo?',
    answer:
      'Riders see it when you are on your way. It is how a woman getting into a stranger\'s vehicle at night checks '
      + 'she has the right one. It is never shown to anyone before you accept a trip.',
  },
  {
    question: 'Why am I not getting requests?',
    answer:
      'You must be online, verified, and allowing location access - requests are offered to the nearest available '
      + 'partners first, then further out.',
  },
  {
    question: 'How do I contact my rider?',
    answer:
      'Through chat on the trip screen, from the moment you accept until the trip ends. Phone numbers are never '
      + 'shared in either direction and cannot be sent through chat. If something needs a call, call us.',
  },
  {
    question: 'What happens if I cancel a trip?',
    answer:
      'You will be asked why, in one tap. Cancelling often relative to the trips you take on is reviewed by a person '
      + 'here - never acted on automatically - so the reason you give is your side of it.',
  },
  {
    question: 'How are my earnings worked out?',
    answer:
      'Earnings are the total of your completed trips. There is no separate payout module yet, so Earnings shows trip '
      + 'totals rather than settled payouts.',
  },
];

/**
 * Static by design - there is no ticketing backend, so this gives real
 * contact routes the device can act on rather than a form that posts
 * nowhere.
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

      <Card className="flex items-center gap-3">
        {/* Was a tall centred block with a large icon and two lines of text,
            most of which was empty space. The same information on one line
            leaves the screen to the things a partner came here to use. */}
        <IconCircle size="lg" tone="soft" icon={<LifeBuoy />} />
        <div>
          <p className="font-heading font-semibold text-text-primary">Driver support</p>
          <p className="text-sm text-text-secondary">We usually reply within a day.</p>
        </div>
      </Card>

      {/* Headed sections over divided cards - the same shape the Profile
          screens use, so a partner meets one convention across the app. */}
      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Get in touch</h2>
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
      </section>

      <FaqList heading="Common questions" items={FAQS} />
    </div>
  );
}

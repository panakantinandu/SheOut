import { LifeBuoy, Mail, Phone, Siren } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, FaqList, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import type { FaqItem } from '@sheout/design-system';
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

/**
 * Answers written against what this app actually does, held as data so the
 * screen below stays about layout. They render through the shared FaqList,
 * which the partner app uses too, so the two cannot drift into presenting
 * the same kind of information two different ways.
 */
const FAQS: FaqItem[] = [
  {
    question: 'How do I cancel a booking?',
    answer:
      'Open the trip from Bookings and use Cancel, any time before the trip starts. You will be asked why, in one '
      + 'tap. Cancelling often enough is looked at by a person here, never acted on automatically.',
  },
  {
    question: 'How do I contact my partner?',
    answer:
      'Through chat on the trip screen, from the moment she accepts until the trip ends. Phone numbers are never '
      + 'shared in either direction, and cannot be sent through chat. If something needs a call, call us and we will '
      + 'handle it.',
  },
  {
    question: 'When do I see who is picking me up?',
    answer:
      'Once she accepts your trip. You will see her name, photo, vehicle and registration number, and her rating. '
      + 'Nothing about her is shown before that, because until she accepts she may not be the one coming.',
  },
  {
    question: 'Who can see my SOS alert?',
    answer:
      'The emergency contacts saved on your account are sent an SMS with your live location, and the alert is raised '
      + 'to SheOut operators.',
  },
  {
    question: 'How am I charged?',
    answer:
      'Per trip, by UPI or cash. No card is stored on your account - see Payment History for what you have paid.',
  },
];

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

      <Card className="flex items-center gap-3">
        {/* Was a tall centred block that was mostly empty space. The same
            information on one line leaves the screen to what she came for. */}
        <IconCircle size="lg" tone="soft" icon={<LifeBuoy />} />
        <div>
          <p className="font-heading font-semibold text-text-primary">We are here to help</p>
          <p className="text-sm text-text-secondary">Reach us any time - we usually reply within a day.</p>
        </div>
      </Card>

      {/* Headed sections over divided cards - the same shape the Profile
          screen uses, so one convention holds across the app. */}
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
              label="Call support"
              sublabel={supportPhone}
              onClick={() => { window.location.href = `tel:${supportPhone}`; }}
            />
          )}
          <ListRow
            icon={<IconCircle color="red" tone="soft" size="sm" icon={<Siren />} />}
            label="Emergency SOS"
            sublabel="Alert your emergency contacts now"
            onClick={() => navigate('/sos')}
          />
        </Card>
      </section>

      <FaqList heading="Common questions" items={FAQS} />
    </div>
  );
}

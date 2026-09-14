import { LifeBuoy, Mail, Phone, Plus, Siren, Scale } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, FaqList, IconCircle, ListRow, SupportTicketList, TopHeader } from '@sheout/design-system';
import type { FaqItem, SupportTicketFilters } from '@sheout/design-system';
import { supportApi } from '../api/client';
import type { SupportTicketCategory, SupportTicketStatus } from '../api/types';

const SUPPORT_EMAIL = 'support@sheout.app';

/**
 * Help & Support: raise an issue, follow the ones already raised, and the
 * direct routes for everything a ticket is the wrong tool for.
 * <p>
 * This used to be static - contact rows and an FAQ - because there was no
 * ticketing backend to post to. Tickets are the main thing here now. The
 * contact rows and the FAQ stay below them rather than going: the SOS row is
 * the one genuinely urgent path on this screen and must not be removed in
 * favour of a queue, and a phone call is still how somebody who cannot type
 * right now reaches a person.
 * <p>
 * The phone number comes from the backend - see SupportController - and the
 * row is hidden when none is configured.
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
  const [grievanceEmail, setGrievanceEmail] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    supportApi
      .getContact()
      .then((contact) => {
        if (!cancelled) {
          setSupportPhone(contact.phoneNumber);
          setGrievanceEmail(contact.grievanceOfficerEmail);
        }
      })
      .catch(() => {
        // Tickets, email and SOS all still work, so a failure here costs one
        // row rather than the screen.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const fetchPage = useCallback(
    (filters: SupportTicketFilters) =>
      supportApi.myTickets({
        page: filters.page,
        status: filters.status as SupportTicketStatus[] | undefined,
        category: filters.category as SupportTicketCategory[] | undefined,
        from: filters.from,
        to: filters.to,
      }),
    []
  );

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Help & Support" onBack={() => navigate('/profile')} />

      <Card className="space-y-3">
        <div className="flex items-center gap-3">
          <IconCircle size="lg" tone="soft" icon={<LifeBuoy />} />
          <div>
            <p className="font-heading font-semibold text-text-primary">We are here to help</p>
            <p className="text-sm text-text-secondary">Tell us what happened and we will reply here.</p>
          </div>
        </div>
        <Button fullWidth size="md" icon={<Plus className="h-4 w-4" />} onClick={() => navigate('/help/new')}>
          Raise an issue
        </Button>
      </Card>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">My tickets</h2>
        <SupportTicketList audience="customer" fetchPage={fetchPage} onOpen={(id) => navigate(`/help/tickets/${id}`)} />
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Other ways to reach us</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow
            icon={<IconCircle color="red" tone="soft" size="sm" icon={<Siren />} />}
            label="Emergency SOS"
            sublabel="In danger right now? Alert your emergency contacts"
            onClick={() => navigate('/sos')}
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
            icon={<IconCircle tone="soft" size="sm" icon={<Mail />} />}
            label="Email us"
            sublabel={SUPPORT_EMAIL}
            onClick={() => { window.location.href = `mailto:${SUPPORT_EMAIL}`; }}
          />
          {/* The DPDP Grievance Officer - for complaints about how your data or
              account is handled, and data-rights requests. */}
          <ListRow
            icon={<IconCircle tone="soft" size="sm" icon={<Scale />} />}
            label="Grievance Officer"
            sublabel={grievanceEmail ?? 'Contact details are being set up'}
            onClick={grievanceEmail ? () => { window.location.href = `mailto:${grievanceEmail}`; } : undefined}
            chevron={Boolean(grievanceEmail)}
          />
        </Card>
      </section>

      <FaqList heading="Common questions" items={FAQS} />
    </div>
  );
}

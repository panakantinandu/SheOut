import { LifeBuoy, Mail, Phone, Plus, Siren, Scale } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  FaqList,
  IconCircle,
  ListRow,
  SupportTicketList,
  TopHeader,
  contentText,
  useAppLanguage,
  faqItemsFromContent,
  useContentSection,
} from '@sheout/design-system';
import type { FaqItem, SupportTicketFilters } from '@sheout/design-system';
import { contentApi, supportApi } from '../api/client';
import type { SupportTicketCategory, SupportTicketStatus } from '../api/types';
import { useTranslation } from '@sheout/design-system';

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

/**
 * The FAQs as they were seeded into the content module (faq.customer.*), kept
 * as the fallback for a first open with no network. Operators edit the live
 * copy in the ops console; this list is not where to change an answer.
 */
/** The built-in answers, in the current language. English is also the fallback for ops-edited copy. */
function faqs(t: (key: string) => string): FaqItem[] {
  return [1, 2, 3, 4, 5].map((n) => ({ question: t(`faq.q${n}`), answer: t(`faq.a${n}`) }));
}

export function HelpSupport() {
  const { t } = useTranslation();
  const lng = useAppLanguage();
  // Operators edit the English in the console; other languages use the
  // translated copy until the content module has per-language entries.
  const fromContent = (section: Record<string, string>, key: string, fallbackKey: string) =>
    lng === 'en' ? contentText(section, key, t(fallbackKey)) : t(fallbackKey);
  const navigate = useNavigate();
  const [supportPhone, setSupportPhone] = useState<string | null>(null);
  const [grievanceEmail, setGrievanceEmail] = useState<string | null>(null);
  const intro = useContentSection('help.customer.', contentApi.getSection);
  const faqCopy = useContentSection('faq.customer.', contentApi.getSection);

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
      <TopHeader variant="back" title={t('help.title')} onBack={() => navigate(-1)} />

      <Card className="space-y-3">
        <div className="flex items-center gap-3">
          <IconCircle size="lg" tone="soft" icon={<LifeBuoy />} />
          <div>
            <p className="font-heading font-semibold text-text-primary">{fromContent(intro, 'help.customer.intro.title', 'help.introTitle')}</p>
            <p className="text-sm text-text-secondary">{fromContent(intro, 'help.customer.intro.subtitle', 'help.introSubtitle')}</p>
          </div>
        </div>
        <Button fullWidth size="md" icon={<Plus className="h-4 w-4" />} onClick={() => navigate('/help/new')}>
          {t('help.raiseIssue')}
        </Button>
      </Card>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('help.myTickets')}</h2>
        <SupportTicketList audience="customer" fetchPage={fetchPage} onOpen={(id) => navigate(`/help/tickets/${id}`)} />
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('help.otherWays')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow
            icon={<IconCircle color="red" tone="soft" size="sm" icon={<Siren />} />}
            label={t('help.sosLabel')}
            sublabel={t('help.sosSub')}
            onClick={() => navigate('/sos')}
          />
          {supportPhone && (
            <ListRow
              icon={<IconCircle tone="soft" size="sm" icon={<Phone />} />}
              label={t('help.callSupport')}
              sublabel={supportPhone}
              onClick={() => { window.location.href = `tel:${supportPhone}`; }}
            />
          )}
          <ListRow
            icon={<IconCircle tone="soft" size="sm" icon={<Mail />} />}
            label={t('help.emailUs')}
            sublabel={SUPPORT_EMAIL}
            onClick={() => { window.location.href = `mailto:${SUPPORT_EMAIL}`; }}
          />
          {/* The DPDP Grievance Officer - for complaints about how your data or
              account is handled, and data-rights requests. */}
          <ListRow
            icon={<IconCircle tone="soft" size="sm" icon={<Scale />} />}
            label={t('help.grievance')}
            sublabel={grievanceEmail ?? t('help.grievancePending')}
            onClick={grievanceEmail ? () => { window.location.href = `mailto:${grievanceEmail}`; } : undefined}
            chevron={Boolean(grievanceEmail)}
          />
        </Card>
      </section>

      <FaqList heading={t('help.faqHeading')} items={lng === 'en' ? faqItemsFromContent(faqCopy, 'faq.customer.', faqs(t)) : faqs(t)} />
    </div>
  );
}

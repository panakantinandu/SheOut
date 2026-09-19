import { LifeBuoy, Mail, Phone, Plus, ShieldCheck, Scale } from 'lucide-react';
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
/**
 * The FAQs as they were seeded into the content module (faq.driver.*), kept
 * as the fallback for a first open with no network. Operators edit the live
 * copy in the ops console; this list is not where to change an answer.
 */
/** The built-in answers, in the current language. English is also the fallback for ops-edited copy. */
function faqs(t: (key: string) => string): FaqItem[] {
  return [1, 2, 3, 4, 5, 6].map((n) => ({ question: t(`faq.q${n}`), answer: t(`faq.a${n}`) }));
}

/**
 * Raise an issue, follow the ones already raised, and the direct routes for
 * anything a ticket is the wrong tool for. This used to be static - there
 * was no ticketing backend - and tickets are now the main thing here; the
 * contact rows and FAQ stay below them.
 * <p>
 * The phone number comes from the backend, not from a constant here. It
 * used to be hardcoded in this file and hardcoded differently in the rider
 * app, so the two apps reached support on two different numbers and fixing
 * either meant a frontend deploy. The row is hidden outright when none is
 * configured rather than offering a dead dial.
 */
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
  const intro = useContentSection('help.driver.', contentApi.getSection);
  const faqCopy = useContentSection('faq.driver.', contentApi.getSection);

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
        // Tickets and email still work, so a failure here costs one row, not the screen.
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
            <p className="font-heading font-semibold text-text-primary">{fromContent(intro, 'help.driver.intro.title', 'help.introTitle')}</p>
            <p className="text-sm text-text-secondary">{fromContent(intro, 'help.driver.intro.subtitle', 'help.introSubtitle')}</p>
          </div>
        </div>
        <Button fullWidth size="md" icon={<Plus className="h-4 w-4" />} onClick={() => navigate('/help/new')}>
          {t('help.raiseIssue')}
        </Button>
      </Card>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('help.myTickets')}</h2>
        <SupportTicketList audience="driver" fetchPage={fetchPage} onOpen={(id) => navigate(`/help/tickets/${id}`)} />
      </section>

      {/* Headed sections over divided cards - the same shape the Profile
          screens use, so a partner meets one convention across the app. */}
      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('help.otherWays')}</h2>
        <Card className="divide-y divide-border p-0">
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
          {supportPhone && (
            <ListRow
              icon={<IconCircle tone="soft" size="sm" icon={<Phone />} />}
              label={t('help.callSupport')}
              sublabel={supportPhone}
              onClick={() => { window.location.href = `tel:${supportPhone}`; }}
            />
          )}
          <ListRow
            icon={<IconCircle tone="soft" size="sm" icon={<ShieldCheck />} />}
            label={t('help.verificationStatus')}
            sublabel={t('help.verificationStatusSub')}
            onClick={() => navigate('/verification')}
          />
        </Card>
      </section>

      <FaqList heading={t('help.faqHeading')} items={lng === 'en' ? faqItemsFromContent(faqCopy, 'faq.driver.', faqs(t)) : faqs(t)} />
    </div>
  );
}

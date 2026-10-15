import { useTranslation } from 'react-i18next';
import { LEGAL_ENTITY, entityValue } from '../legal/entity';
import { highlightPlaceholders } from './highlightPlaceholders';
import { cn } from '../lib/cn';

/** Every legal page, in the order a reader looks for them. Keys are the ds `legal.pages.*` labels. */
export const LEGAL_LINKS = [
  { path: '/terms', label: 'terms' },
  { path: '/privacy', label: 'privacy' },
  { path: '/partner-terms', label: 'partnerTerms' },
  { path: '/refunds', label: 'refunds' },
  { path: '/parcel-rules', label: 'parcelRules' },
  { path: '/safety-guidelines', label: 'safetyGuidelines' },
  { path: '/storage-policy', label: 'storagePolicy' },
  { path: '/grievance', label: 'grievance' },
  { path: '/contact', label: 'contact' },
  { path: '/delete-account', label: 'deleteAccount' },
] as const;

export interface LegalFooterProps {
  /**
   * Where the pages live. Empty in the apps (same site); the public site
   * passes the customer app's address, where the pages are served.
   */
  baseUrl?: string;
  /** For a dark background, like the public site's footer. */
  tone?: 'default' | 'inverse';
  className?: string;
}

/**
 * The business details the law expects on every public page - legal name,
 * CIN, registered office, contact and the Grievance Officer - and a link to
 * each legal page. Read from legal-entity.config.json; a value the owner has
 * not supplied yet shows as a visible placeholder, never as a blank.
 */
export function LegalFooter({ baseUrl = '', tone = 'default', className }: LegalFooterProps) {
  const { t } = useTranslation('ds');
  const e = LEGAL_ENTITY;
  const muted = tone === 'inverse' ? 'text-white/70' : 'text-text-secondary';
  const strong = tone === 'inverse' ? 'text-white' : 'text-text-primary';
  return (
    <footer className={cn('space-y-3 border-t border-border pt-5 text-xs leading-relaxed', muted, className)} data-testid="legal-footer">
      <nav aria-label={t('legal.footer.heading')}>
        <ul className="flex flex-wrap gap-x-1 gap-y-0">
          {LEGAL_LINKS.map((l) => (
            <li key={l.path}>
              <a href={`${baseUrl}${l.path}`} className={cn('inline-flex min-h-[44px] items-center px-1.5 underline', strong)}>
                {t(`legal.pages.${l.label}`)}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      <p className={cn('font-semibold', strong)}>{e.brandLine}</p>
      <p>
        {highlightPlaceholders(
          `${e.legalName} · CIN ${entityValue(e.cin)} · ${entityValue(e.registeredAddress)}`
        )}
      </p>
      <p>
        <a href={`mailto:${e.supportEmail}`} className="inline-flex min-h-[44px] items-center underline">
          {e.supportEmail}
        </a>
        {' · '}
        {highlightPlaceholders(entityValue(e.supportPhone))}
      </p>
      <p>
        {highlightPlaceholders(
          t('legal.footer.grievance', { name: entityValue(e.grievanceOfficer.name), email: e.grievanceOfficer.email })
        )}
      </p>
    </footer>
  );
}

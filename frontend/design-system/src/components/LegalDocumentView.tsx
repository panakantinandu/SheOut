import { Card } from './Card';
import { TopHeader } from './TopHeader';
import { TODO_LAWYER, TODO_LEGAL, type LegalDocument } from '../legal/content';
import { useAppLanguage } from '../i18n';
import { useTranslation } from 'react-i18next';

export interface LegalDocumentViewProps {
  document: LegalDocument;
  onBack: () => void;
}

/**
 * Renders a privacy policy or terms document. Shared, because both apps
 * show the same two documents and two copies would drift into two different
 * promises.
 * <p>
 * Placeholders are rendered in the danger colour rather than hidden. A
 * half-finished policy that looks finished is the failure mode worth
 * avoiding: anyone reading this should be able to see at a glance that the
 * company details are still outstanding.
 */
export function LegalDocumentView({ document, onBack }: LegalDocumentViewProps) {
  const { t } = useTranslation('ds');
  const lng = useAppLanguage();
  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={document.title} onBack={onBack} />

      {/* Legal text is not machine-translated: a wrong word in a contract is
          a legal problem, not a typo. Said plainly rather than left to be
          discovered. */}
      {lng !== 'en' && (
        <Card tone="warning" data-testid="legal-english-only">
          <p className="text-sm text-text-primary">{t('legal.englishOnly')}</p>
        </Card>
      )}

      <Card className="space-y-2">
        <p className="text-xs font-medium uppercase tracking-wide text-text-secondary">{document.effective}</p>
        <p className="text-sm leading-relaxed text-text-primary">{document.intro}</p>
      </Card>

      {document.sections.map((section) => (
        <section key={section.heading}>
          <h2 className="mb-2 font-heading text-section text-text-primary">{section.heading}</h2>
          <Card className="space-y-3">
            {section.paragraphs.map((text, i) => (
              <p key={i} className="text-sm leading-relaxed text-text-secondary">
                {highlightPlaceholders(text)}
              </p>
            ))}
            {section.bullets && (
              <ul className="space-y-2">
                {section.bullets.map((text, i) => (
                  <li key={i} className="flex gap-2 text-sm leading-relaxed text-text-secondary">
                    <span aria-hidden="true" className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
                    <span>{highlightPlaceholders(text)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </section>
      ))}
    </div>
  );
}

/**
 * Makes an unfinished section impossible to mistake for a finished one -
 * both a fact only the company can supply and wording a lawyer has not yet
 * approved. Exported for the partner app's consent step.
 */
export function highlightPlaceholders(text: string) {
  const markers = [TODO_LEGAL, TODO_LAWYER].filter((m) => text.includes(m));
  if (!markers.length) return text;
  const pattern = new RegExp(`(${markers.map((m) => m.replace(/[[\]]/g, '\\$&')).join('|')})`);
  return text.split(pattern).map((part, i) =>
    markers.includes(part) ? (
      <span key={i} className="font-semibold text-danger">
        {part}
      </span>
    ) : (
      part
    )
  );
}

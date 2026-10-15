import { useEffect } from 'react';
import { X } from 'lucide-react';
import { Card } from './Card';
import { TopHeader } from './TopHeader';
import { SheOutWordmark } from './SheOutLogo';
import { type LegalDocument, type LegalPath } from '../legal/content';
import { highlightPlaceholders } from './highlightPlaceholders';
import { LegalFooter } from './LegalFooter';
import { useAppLanguage } from '../i18n';
import { useTranslation } from 'react-i18next';

export interface LegalDocumentViewProps {
  document: LegalDocument;
  onBack: () => void;
  /**
   * Shown as a document in its own browser tab (see openLegalDocument), not
   * as a screen of the app: the SheOut name and the title instead of a back
   * arrow that would walk into the app.
   */
  standalone?: boolean;
  /** The tab was opened by the app, so it may close itself. */
  canClose?: boolean;
}

/** Marks a legal page opened from the app in a tab of its own. */
const TAB_PARAM = 'view=tab';

/**
 * Opens the Terms or the Privacy Policy in a new browser tab, at its own
 * address. In place, it unmounted whatever she was in the middle of - the
 * number and code on sign-in, her name and date of birth on Complete
 * profile - and back brought her to an empty form. A tab leaves all of that
 * where it was, and the document is a page she can share, print or keep.
 */
export function openLegalDocument(path: LegalPath | '/delete-account') {
  window.open(`${path}?${TAB_PARAM}`, '_blank', 'noopener');
}

/** True on a legal page that was opened by openLegalDocument. */
export function openedAsLegalTab(search: string): boolean {
  return new URLSearchParams(search).get('view') === 'tab';
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
export function LegalDocumentView({ document, onBack, standalone = false, canClose = false }: LegalDocumentViewProps) {
  const { t } = useTranslation('ds');
  const lng = useAppLanguage();
  // The browser tab says which document it is.
  useEffect(() => {
    if (!standalone) return;
    const before = globalThis.document.title;
    globalThis.document.title = `${document.title} · SheOut`;
    return () => {
      globalThis.document.title = before;
    };
  }, [standalone, document.title]);
  return (
    <div className="space-y-6">
      {standalone ? (
        <header className="space-y-4" data-testid="legal-standalone-header">
          <div className="flex items-center justify-between gap-3">
            <SheOutWordmark className="w-28" />
            {canClose && (
              <button
                type="button"
                onClick={() => window.close()}
                className="flex min-h-[44px] items-center gap-1.5 rounded-full border border-border bg-surface px-4 text-sm font-semibold text-text-primary"
                data-testid="legal-close"
              >
                <X className="h-4 w-4" aria-hidden="true" />
                {t('common.close')}
              </button>
            )}
          </div>
          <h1 className="font-heading text-title text-text-primary">{document.title}</h1>
        </header>
      ) : (
        <TopHeader variant="back" title={document.title} onBack={onBack} />
      )}

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

      <LegalFooter />
    </div>
  );
}

export { highlightPlaceholders };

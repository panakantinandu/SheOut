import { Check, Languages } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { LANGUAGES, type AppLanguage, useAppLanguage } from '../i18n';
import { cn } from '../lib/cn';
import { IconCircle } from './IconCircle';

export interface LanguagePickerProps {
  open: boolean;
  onClose: () => void;
  /** Called with the chosen code; the caller switches, saves and closes. */
  onSelect: (language: AppLanguage) => void;
}

/**
 * Choosing the app's language.
 * <p>
 * Each option is written in its own script ("తెలుగు", "हिन्दी"), with the
 * English name under it, so somebody who cannot read the language the app
 * is currently in can still find hers. A bottom sheet rather than a centred
 * dialog - it is reached from the drawer, with a thumb.
 */
export function LanguagePicker({ open, onClose, onSelect }: LanguagePickerProps) {
  const { t } = useTranslation('ds');
  const current = useAppLanguage();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[60] flex items-end justify-center bg-text-primary/40"
      role="dialog"
      aria-modal="true"
      aria-label={t('language.title')}
      onClick={onClose}
      data-testid="language-picker"
    >
      <div
        className="w-full max-w-md animate-fade-slide-in rounded-t-[28px] bg-surface px-screen pb-8 pt-3 shadow-card"
        onClick={(e) => e.stopPropagation()}
      >
        <span className="mx-auto mb-4 block h-1.5 w-10 rounded-full bg-border" aria-hidden="true" />
        <div className="mb-4 flex items-center gap-3">
          <IconCircle tone="soft" icon={<Languages />} />
          <div>
            <p className="font-heading text-lg font-semibold text-text-primary">{t('language.title')}</p>
            <p className="text-sm text-text-secondary">{t('language.subtitle')}</p>
          </div>
        </div>
        <ul className="space-y-2" role="radiogroup" aria-label={t('language.title')}>
          {LANGUAGES.map((language) => {
            const selected = language.code === current;
            return (
              <li key={language.code}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  lang={language.code}
                  onClick={() => onSelect(language.code)}
                  className={cn(
                    'flex w-full items-center justify-between rounded-card border px-4 py-3 text-left transition-colors active:scale-[0.99]',
                    selected ? 'border-primary bg-primary-light' : 'border-border bg-surface hover:bg-background'
                  )}
                  data-testid={`language-${language.code}`}
                >
                  <span>
                    <span className="block font-heading text-base font-semibold text-text-primary">{language.nativeName}</span>
                    {language.nativeName !== language.englishName && (
                      <span className="block text-xs text-text-secondary" lang="en">
                        {language.englishName}
                      </span>
                    )}
                  </span>
                  {selected && <Check className="h-5 w-5 text-primary" aria-hidden="true" />}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}

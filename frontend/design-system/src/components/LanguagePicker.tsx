import { Check, Languages } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { LANGUAGES, type AppLanguage, useAppLanguage } from '../i18n';
import { cn } from '../lib/cn';
import { Overlay } from './Overlay';
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

  return (
    <Overlay open={open} label={t('language.title')} align="sheet" onDismiss={onClose}>
      <div
        className="w-full rounded-t-[28px] bg-surface px-screen pb-8 pt-3 shadow-card motion-safe:animate-sheet-up"
        data-testid="language-picker"
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
    </Overlay>
  );
}

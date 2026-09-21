import { Check, Monitor, Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';
import { IconCircle } from './IconCircle';
import { Overlay } from './Overlay';
import { useTheme, type ThemeChoice } from '../lib/theme';

const OPTIONS: { value: ThemeChoice; icon: typeof Sun; key: string; noteKey: string }[] = [
  { value: 'light', icon: Sun, key: 'theme.light', noteKey: 'theme.lightNote' },
  { value: 'dark', icon: Moon, key: 'theme.dark', noteKey: 'theme.darkNote' },
  { value: 'system', icon: Monitor, key: 'theme.system', noteKey: 'theme.systemNote' },
];

export interface ThemePickerProps {
  open: boolean;
  onClose: () => void;
}

/**
 * Light, dark or system, from the menu.
 * <p>
 * The same three choices as the toggle in Profile, in the shape of the
 * language picker beside it in the menu. Both exist on purpose: the setting
 * lives in Profile with the other account settings, and the menu row is how
 * anybody finds out it exists at all. A dark mode nobody knows about is a
 * dark mode nobody uses, and the menu is the first place people look for
 * "how do I change how this looks".
 * <p>
 * It closes on choosing, because the whole screen changing colour under her
 * finger is the confirmation - a Done button would be asking her to confirm
 * something she can already see.
 */
export function ThemePicker({ open, onClose }: ThemePickerProps) {
  const { t } = useTranslation('ds');
  const [choice, setChoice] = useTheme();

  return (
    <Overlay open={open} label={t('theme.label')} align="sheet" onDismiss={onClose}>
      <div
        className="w-full rounded-t-[28px] bg-surface px-screen pb-8 pt-3 shadow-card motion-safe:animate-sheet-up"
        data-testid="theme-picker"
      >
        <span className="mx-auto mb-4 block h-1.5 w-10 rounded-full bg-border" aria-hidden="true" />
        <div className="mb-4 flex items-center gap-3">
          <IconCircle tone="soft" icon={<Moon />} />
          <div>
            <p className="font-heading text-lg font-semibold text-text-primary">{t('theme.label')}</p>
            <p className="text-sm text-text-secondary">{t('theme.description')}</p>
          </div>
        </div>
        <ul className="space-y-2" role="radiogroup" aria-label={t('theme.label')}>
          {OPTIONS.map((option) => {
            const Icon = option.icon;
            const selected = choice === option.value;
            return (
              <li key={option.value}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => {
                    setChoice(option.value);
                    onClose();
                  }}
                  data-testid={`theme-option-${option.value}`}
                  className={cn(
                    'flex w-full items-center gap-3 rounded-card border px-4 py-3 text-left transition-colors active:scale-[0.99]',
                    selected ? 'border-primary bg-primary-light' : 'border-border bg-surface'
                  )}
                >
                  <Icon className="h-5 w-5 text-primary" aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-text-primary">{t(option.key)}</span>
                    <span className="block text-xs text-text-secondary">{t(option.noteKey)}</span>
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

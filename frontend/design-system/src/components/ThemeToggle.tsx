import { Monitor, Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';
import { useTheme, type ThemeChoice } from '../lib/theme';

const OPTIONS: { value: ThemeChoice; icon: typeof Sun; key: string }[] = [
  { value: 'light', icon: Sun, key: 'theme.light' },
  { value: 'dark', icon: Moon, key: 'theme.dark' },
  { value: 'system', icon: Monitor, key: 'theme.system' },
];

export interface ThemeToggleProps {
  className?: string;
}

/**
 * Light, dark, or whatever the phone is doing.
 * <p>
 * THREE CHOICES, NOT A SWITCH. A two-state switch has to decide what it
 * means on a phone that changes theme at sunset: either it ignores the
 * phone, or it silently overrides what she picked. Naming "System" as its
 * own option is the honest version, and it is the default, so the app
 * matches everything else on her screen until she says otherwise.
 * <p>
 * The choice is stored per device rather than on the account: it is about
 * this screen, in this light, and somebody who reads in bed on her phone
 * and at a desk on a laptop wants different answers on each.
 */
export function ThemeToggle({ className }: ThemeToggleProps) {
  const { t } = useTranslation('ds');
  const [choice, setChoice] = useTheme();

  return (
    <div
      className={cn('flex gap-1 rounded-full border border-border bg-background p-1', className)}
      role="radiogroup"
      aria-label={t('theme.label')}
      data-testid="theme-toggle"
    >
      {OPTIONS.map((option) => {
        const Icon = option.icon;
        const selected = choice === option.value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => setChoice(option.value)}
            data-testid={`theme-${option.value}`}
            className={cn(
              'flex flex-1 items-center justify-center gap-1.5 rounded-full px-3 py-2 text-xs font-semibold transition-colors',
              selected ? 'bg-primary text-text-inverse' : 'text-text-secondary'
            )}
          >
            <Icon className="h-4 w-4" aria-hidden="true" />
            {t(option.key)}
          </button>
        );
      })}
    </div>
  );
}

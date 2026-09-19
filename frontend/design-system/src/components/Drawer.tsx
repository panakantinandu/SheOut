import { ChevronRight, X } from 'lucide-react';
import { useEffect, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';

export interface DrawerItem {
  key: string;
  label: string;
  icon: ReactNode;
  /** A short line under the label - the current language, "Coming soon". */
  sublabel?: string;
  onClick: () => void;
  /** Danger styling, for Log Out. */
  tone?: 'default' | 'danger';
}

export interface DrawerSection {
  key: string;
  /** Omitted for the first section, which follows the header directly. */
  title?: string;
  items: DrawerItem[];
}

export interface DrawerProps {
  open: boolean;
  onClose: () => void;
  /** Who is signed in - avatar, name, a line under it. */
  header: ReactNode;
  sections: DrawerSection[];
  /** Small print at the bottom, e.g. the app version. */
  footer?: ReactNode;
}

/**
 * The slide-out menu behind the header's menu button.
 * <p>
 * For the things that are neither a tab nor part of a trip: language, the
 * legal pages, support, signing out. It deliberately does not repeat the
 * bottom tabs - a menu entry that opens the same screen as the tab under your
 * thumb is two ways to the same place, and it is what the menu button used
 * to do.
 * <p>
 * Stays mounted while closed so it can slide back out rather than vanish;
 * hidden from assistive technology and the tab order until opened.
 */
export function Drawer({ open, onClose, header, sections, footer }: DrawerProps) {
  const { t } = useTranslation('ds');
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    // Focus into the panel so keyboard and screen-reader users land inside it.
    panel.current?.focus();
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  return (
    <div className={cn('fixed inset-0 z-50', open ? 'pointer-events-auto' : 'pointer-events-none')} aria-hidden={!open}>
      <div
        className={cn(
          'absolute inset-0 bg-text-primary/40 transition-opacity duration-300',
          open ? 'opacity-100' : 'opacity-0'
        )}
        onClick={onClose}
      />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={t('drawer.label')}
        tabIndex={-1}
        className={cn(
          'absolute inset-y-0 left-0 flex w-[82%] max-w-[320px] flex-col bg-surface shadow-card outline-none transition-transform duration-300 ease-out',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
        data-testid="drawer"
        {...(open ? {} : { inert: '' })}
      >
        <div className="relative bg-gradient-to-br from-primary to-primary-dark px-5 pb-5 pt-8 text-text-inverse">
          <button
            type="button"
            onClick={onClose}
            aria-label={t('common.close')}
            className="absolute right-3 top-3 flex h-9 w-9 items-center justify-center rounded-full text-text-inverse/90 hover:bg-text-inverse/10"
          >
            <X className="h-5 w-5" />
          </button>
          {header}
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-3">
          {sections.map((section, index) => (
            <div key={section.key} className={cn(index > 0 && 'mt-3 border-t border-border pt-3')}>
              {section.title && (
                <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-text-secondary">{section.title}</p>
              )}
              <ul>
                {section.items.map((item) => (
                  <li key={item.key}>
                    <button
                      type="button"
                      onClick={item.onClick}
                      className={cn(
                        'flex w-full items-center gap-3 rounded-card px-3 py-2.5 text-left transition-colors hover:bg-background active:scale-[0.99]',
                        item.tone === 'danger' ? 'text-danger' : 'text-text-primary'
                      )}
                      data-testid={`drawer-${item.key}`}
                    >
                      <span
                        className={cn(
                          'flex h-9 w-9 shrink-0 items-center justify-center rounded-full [&>svg]:h-[18px] [&>svg]:w-[18px]',
                          item.tone === 'danger' ? 'bg-danger/10 text-danger' : 'bg-primary-light text-primary'
                        )}
                      >
                        {item.icon}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-sm font-semibold">{item.label}</span>
                        {item.sublabel && <span className="block truncate text-xs text-text-secondary">{item.sublabel}</span>}
                      </span>
                      {item.tone !== 'danger' && <ChevronRight className="h-4 w-4 shrink-0 text-text-secondary" aria-hidden="true" />}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        {footer && <div className="border-t border-border px-5 py-3 text-xs text-text-secondary">{footer}</div>}
      </div>
    </div>
  );
}

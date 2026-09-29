import { Search, SlidersHorizontal, X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Button } from './Button';
import { Card } from './Card';
import { Overlay } from './Overlay';
import { TextField } from './TextField';
import { useTranslation } from 'react-i18next';

export interface ListFilterBarProps {
  /** Omit entirely where text search is meaningless - see the comment below. */
  search?: {
    value: string;
    placeholder: string;
    onChange: (value: string) => void;
  };
  /** The filter controls themselves, rendered inside the collapsible panel. */
  children: ReactNode;
  /** How many filters are currently narrowing the list. Drives the badge and the Clear button. */
  activeCount: number;
  onClearAll: () => void;
  /** Start with the panel open, e.g. when arriving from a link that already has filters. */
  defaultOpen?: boolean;
  /**
   * Where the filters open. `inline` (the default) drops a panel under the
   * bar, which suits a history list with two or three controls. `sheet`
   * raises them from the bottom of the screen, for a screen whose bar is
   * pinned while the list scrolls: a tall inline panel there would sit on
   * top of the very list it narrows.
   */
  presentation?: 'inline' | 'sheet';
  /** Sheet only: how many results the filters leave, for its "Show N results" button. Omit while unknown. */
  resultCount?: number;
}

/**
 * The search box and filter panel above a history list.
 * <p>
 * The filters collapse behind one button rather than sitting permanently on
 * screen. On a phone a date range, a status and a category selector stacked
 * above the list would push the list itself off the fold, which is the
 * thing the person came to read. The count badge means a collapsed panel
 * still says it is doing something - a hidden filter that silently shortens
 * a list is how people conclude their history has been lost.
 * <p>
 * `search` is optional because text search is not always meaningful. A
 * payment has an amount, a method, a status and two dates, and nothing
 * worth typing at; offering a box there that matched nothing would be worse
 * than offering none. Payment History passes date, status and amount range
 * instead.
 */
export function ListFilterBar({
  search,
  children,
  activeCount,
  onClearAll,
  defaultOpen = false,
  presentation = 'inline',
  resultCount,
}: ListFilterBarProps) {
  const { t } = useTranslation('ds');
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        {search && (
          <div className="min-w-0 flex-1">
            <TextField
              icon={<Search className="h-4 w-4 shrink-0 text-text-secondary" />}
              type="search"
              className="min-w-0 text-ellipsis"
              placeholder={search.placeholder}
              value={search.value}
              onChange={(e) => search.onChange(e.target.value)}
            />
          </div>
        )}
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-label={open ? t('filters.hide') : t('filters.show')}
          className={
            activeCount > 0
              ? 'flex h-14 shrink-0 items-center gap-2 rounded-input border border-primary bg-primary-light px-4 text-sm font-semibold text-primary'
              : 'flex h-14 shrink-0 items-center gap-2 rounded-input border border-border px-4 text-sm font-medium text-text-secondary'
          }
        >
          <SlidersHorizontal className="h-4 w-4" />
          Filters
          {activeCount > 0 && (
            <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-2 text-xs font-bold text-text-inverse">
              {activeCount}
            </span>
          )}
        </button>
      </div>

      {presentation === 'sheet' && (
        <Overlay open={open} label={t('filters.title')} align="sheet" onDismiss={() => setOpen(false)}>
          <div
            className="flex max-h-[85vh] w-full flex-col rounded-t-[28px] bg-surface shadow-overlay motion-safe:animate-sheet-up"
            data-testid="filter-sheet"
          >
            <div className="px-screen pt-3">
              <span className="mx-auto mb-3 block h-1.5 w-10 rounded-full bg-border" aria-hidden="true" />
              <div className="mb-3 flex items-center justify-between gap-3">
                <p className="font-heading text-section text-text-primary">{t('filters.title')}</p>
                {activeCount > 0 && (
                  <button type="button" onClick={onClearAll} className="text-sm font-semibold text-danger">
                    {t('filters.clear')}
                  </button>
                )}
              </div>
            </div>
            <div className="flex-1 space-y-5 overflow-y-auto px-screen pb-4">{children}</div>
            <div className="border-t border-border px-screen pb-6 pt-3">
              <Button fullWidth onClick={() => setOpen(false)} data-testid="filter-sheet-done">
                {resultCount === undefined ? t('filters.done') : t('filters.showResults', { count: resultCount })}
              </Button>
            </div>
          </div>
        </Overlay>
      )}

      {presentation === 'inline' && open && (
        <Card className="space-y-4">
          {children}
          {activeCount > 0 && (
            <button
              type="button"
              onClick={onClearAll}
              className="flex items-center gap-2 text-sm font-medium text-danger"
            >
              <X className="h-4 w-4" />
              Clear {activeCount === 1 ? 'filter' : 'all filters'}
            </button>
          )}
        </Card>
      )}
    </div>
  );
}

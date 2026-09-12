import { Search, SlidersHorizontal, X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Card } from './Card';
import { TextField } from './TextField';

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
}: ListFilterBarProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        {search && (
          <div className="min-w-0 flex-1">
            <TextField
              icon={<Search className="h-4 w-4 text-text-secondary" />}
              type="search"
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
          aria-label={open ? 'Hide filters' : 'Show filters'}
          className={
            activeCount > 0
              ? 'flex h-14 shrink-0 items-center gap-2 rounded-input border border-primary bg-primary-light px-4 text-sm font-semibold text-primary'
              : 'flex h-14 shrink-0 items-center gap-2 rounded-input border border-border px-4 text-sm font-medium text-text-secondary'
          }
        >
          <SlidersHorizontal className="h-4 w-4" />
          Filters
          {activeCount > 0 && (
            <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-xs font-bold text-text-inverse">
              {activeCount}
            </span>
          )}
        </button>
      </div>

      {open && (
        <Card className="space-y-4">
          {children}
          {activeCount > 0 && (
            <button
              type="button"
              onClick={onClearAll}
              className="flex items-center gap-1.5 text-sm font-medium text-danger"
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

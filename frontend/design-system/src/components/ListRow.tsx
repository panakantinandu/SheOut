import { ChevronRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../lib/cn';

export interface ListRowProps {
  /** Pass a raw icon or a fully-configured <IconCircle /> - ListRow doesn't wrap it. */
  icon?: ReactNode;
  label: string;
  sublabel?: string;
  /** 'row' (icon + label + chevron, e.g. location pickers, profile menu) or
   *  'stacked' (icon above label, no chevron, e.g. wallet quick actions). */
  layout?: 'row' | 'stacked';
  /**
   * Whether the row supplies its own padding. True by default, because the
   * common case by far is a row inside a divided `p-0` card, where the row
   * has to space itself.
   * <p>
   * It used to have no padding at all and every caller was expected to wrap
   * it in a padded div. Both Profile screens did; both Help screens and the
   * SOS list did not, so identical markup rendered as comfortable menu rows
   * on one screen and a cramped, squashed list on another. A component whose
   * appearance depends on each caller remembering an undocumented wrapper
   * will drift, and it did.
   * <p>
   * An explicit prop rather than something a caller overrides with a class:
   * cn() here is plain clsx, so a caller passing `p-0` would not reliably
   * beat a default `p-4` - which utility wins is decided by stylesheet
   * order, not the order they are listed. This project has been bitten by
   * exactly that before, with colour utilities on Card and AmountText.
   */
  padded?: boolean;
  chevron?: boolean;
  rightSlot?: ReactNode;
  onClick?: () => void;
  className?: string;
}

export function ListRow({
  icon,
  label,
  sublabel,
  layout = 'row',
  padded = true,
  chevron = true,
  rightSlot,
  onClick,
  className,
}: ListRowProps) {
  const Wrapper = onClick ? 'button' : 'div';

  if (layout === 'stacked') {
    return (
      <Wrapper
        type={onClick ? 'button' : undefined}
        onClick={onClick}
        className={cn('flex flex-col items-center gap-2 text-center', className)}
      >
        {icon}
        <span className="text-sm font-medium text-text-primary">{label}</span>
      </Wrapper>
    );
  }

  return (
    <Wrapper
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={cn('flex w-full items-center gap-3 text-left', padded && 'p-4', className)}
    >
      {icon}
      <span className="flex-1 min-w-0">
        <span className="block truncate text-sm font-medium text-text-primary">{label}</span>
        {sublabel && <span className="block truncate text-xs text-text-secondary">{sublabel}</span>}
      </span>
      {rightSlot ?? (chevron && <ChevronRight className="h-4 w-4 shrink-0 text-text-secondary" />)}
    </Wrapper>
  );
}

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
      className={cn('flex w-full items-center gap-3 text-left', className)}
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

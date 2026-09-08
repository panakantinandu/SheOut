import type { ReactNode } from 'react';
import { cn } from '../lib/cn';

export interface BottomNavItem {
  key: string;
  label: string;
  icon: ReactNode;
  active?: boolean;
  /** The floating circular center item (SOS on customer screens). */
  raised?: boolean;
  onClick?: () => void;
}

export interface BottomNavBarProps {
  items: BottomNavItem[];
  className?: string;
}

/**
 * Home / Bookings / Wallet / Profile row, with support for one raised
 * circular item (SOS) that floats above the bar rather than sitting flush
 * in it - matches every customer-app footer in the mockup.
 */
export function BottomNavBar({ items, className }: BottomNavBarProps) {
  return (
    <nav
      className={cn(
        'flex items-end justify-between bg-surface rounded-t-card shadow-card px-2 pb-2 pt-3',
        className
      )}
    >
      {items.map((item) =>
        item.raised ? (
          <button
            key={item.key}
            type="button"
            onClick={item.onClick}
            className="flex flex-1 flex-col items-center gap-1"
          >
            <span className="-mt-8 flex h-14 w-14 items-center justify-center rounded-full bg-danger text-text-inverse shadow-raised [&_svg]:w-6 [&_svg]:h-6">
              {item.icon}
            </span>
            <span className="text-xs font-medium text-danger">{item.label}</span>
          </button>
        ) : (
          <button
            key={item.key}
            type="button"
            onClick={item.onClick}
            className={cn(
              'flex flex-1 flex-col items-center gap-1 py-1 [&_svg]:w-5 [&_svg]:h-5',
              item.active ? 'text-primary' : 'text-text-secondary'
            )}
          >
            {item.icon}
            <span className="text-xs font-medium">{item.label}</span>
          </button>
        )
      )}
    </nav>
  );
}

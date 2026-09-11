import { ArrowLeft, Bell, Menu } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../lib/cn';

interface BackHeaderProps {
  variant: 'back';
  title: string;
  onBack?: () => void;
  rightSlot?: ReactNode;
  /**
   * Centres the title between the back button and the right slot, as the
   * mockup's Partner Dashboard does. Off by default, since every other
   * back-header screen sits the title next to the arrow.
   */
  centerTitle?: boolean;
  className?: string;
}

interface GreetingHeaderProps {
  variant: 'greeting';
  title: string;
  subtitle?: string;
  onMenuClick?: () => void;
  onBellClick?: () => void;
  className?: string;
}

export type TopHeaderProps = BackHeaderProps | GreetingHeaderProps;

/**
 * Two shapes seen across the mockup: a back-arrow + screen title (Bike
 * Taxi, Wallet, My Bookings, ...), and the Home screen's greeting + menu +
 * notification bell.
 */
export function TopHeader(props: TopHeaderProps) {
  if (props.variant === 'back') {
    return (
      <header className={cn('flex items-center gap-3', props.className)}>
        <button
          type="button"
          onClick={props.onBack}
          aria-label="Go back"
          className="flex h-9 w-9 items-center justify-center rounded-full text-text-primary hover:bg-background"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <h1
          className={cn(
            'font-heading text-lg font-semibold text-text-primary',
            props.centerTitle && 'flex-1 text-center'
          )}
        >
          {props.title}
        </h1>
        {props.rightSlot ? (
          <div className={cn(!props.centerTitle && 'ml-auto')}>{props.rightSlot}</div>
        ) : (
          // Keeps a centred title optically centred by balancing the back
          // button's width on the right.
          props.centerTitle && <span className="h-9 w-9" aria-hidden="true" />
        )}
      </header>
    );
  }

  return (
    <header className={cn('flex items-center justify-between', props.className)}>
      <button
        type="button"
        onClick={props.onMenuClick}
        aria-label="Open menu"
        className="flex h-9 w-9 items-center justify-center rounded-full text-text-primary hover:bg-background"
      >
        <Menu className="h-5 w-5" />
      </button>
      <div className="flex-1 px-3">
        <p className="font-heading text-lg font-semibold text-text-primary">{props.title}</p>
        {props.subtitle && <p className="text-sm text-text-secondary">{props.subtitle}</p>}
      </div>
      <button
        type="button"
        onClick={props.onBellClick}
        aria-label="Notifications"
        className="flex h-9 w-9 items-center justify-center rounded-full text-text-primary hover:bg-background"
      >
        <Bell className="h-5 w-5" />
      </button>
    </header>
  );
}

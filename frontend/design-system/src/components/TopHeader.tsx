import { ArrowLeft, Bell, Menu, Sparkles } from 'lucide-react';
import { Fragment, type ReactNode } from 'react';
import { cn } from '../lib/cn';
import { useTranslation } from 'react-i18next';

interface BackHeaderProps {
  /**
   * 'back' for a screen you arrived at from somewhere; 'plain' for a
   * top-level destination reached from the tab bar, which has nothing
   * behind it and so must not show an arrow.
   */
  variant: 'back' | 'plain';
  title: string;
  onBack?: () => void;
  rightSlot?: ReactNode;
  /**
   * Centres the title between the back button and the right slot, as the
   * mockup's Partner Dashboard does. Off by default, since every other
   * back-header screen sits the title next to the arrow.
   */
  centerTitle?: boolean;
  /**
   * A menu button where the back arrow would be - for a top-level screen
   * that opens the app drawer. Only used with the plain variant.
   */
  onMenuClick?: () => void;
  className?: string;
}

interface GreetingHeaderProps {
  variant: 'greeting';
  title: string;
  /** A line under the title - a string, or something livelier such as RotatingText. */
  subtitle?: ReactNode;
  /** A small picture before the title, such as the time of day's SkyIcon. */
  titleIcon?: ReactNode;
  onMenuClick?: () => void;
  onBellClick?: () => void;
  /** Unread notifications; the bell shows a count above zero. */
  unreadCount?: number;
  className?: string;
}

export type TopHeaderProps = BackHeaderProps | GreetingHeaderProps;

/**
 * Two shapes seen across the mockup: a back-arrow + screen title (Bike
 * Taxi, Wallet, My Bookings, ...), and the Home screen's greeting + menu +
 * notification bell.
 */
export function TopHeader(props: TopHeaderProps) {
  const { t } = useTranslation('ds');
  if (props.variant !== 'greeting') {
    return (
      <header className={cn('flex items-center gap-3', props.className)}>
        {props.variant === 'back' ? (
          <button
            type="button"
            onClick={props.onBack}
            aria-label={t('header.back')}
            className="-m-1 flex h-11 w-11 items-center justify-center rounded-full text-text-primary hover:bg-background"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
        ) : props.onMenuClick ? (
          <button
            type="button"
            onClick={props.onMenuClick}
            aria-label={t('header.menu')}
            className="-m-1 flex h-11 w-11 items-center justify-center rounded-full text-text-primary hover:bg-background"
            data-testid="menu-button"
          >
            <Menu className="h-5 w-5" />
          </button>
        ) : (
          props.centerTitle && (
            // Holds the arrow's width so the title stays optically centred.
            <span className="-m-1 h-11 w-11" aria-hidden="true" />
          )
        )}
        <h1
          // Keyed by the title, so a screen that changes its title (Live Track
          // to Trip History) plays it again.
          key={props.title}
          className={cn(
            'min-w-0 font-heading text-section text-text-primary',
            props.centerTitle && 'flex-1 text-center'
          )}
        >
          <AnimatedTitle text={props.title} />
        </h1>
        {props.rightSlot ? (
          <div className={cn(!props.centerTitle && 'ml-auto')}>{props.rightSlot}</div>
        ) : (
          // Keeps a centred title optically centred by balancing the back
          // button's width on the right.
          props.centerTitle && <span className="-m-1 h-11 w-11" aria-hidden="true" />
        )}
      </header>
    );
  }

  return (
    <header className={cn('flex items-center justify-between', props.className)}>
      <button
        type="button"
        onClick={props.onMenuClick}
        aria-label={t('header.menu')}
        className="-m-1 flex h-11 w-11 items-center justify-center rounded-full text-text-primary hover:bg-background"
        data-testid="menu-button"
      >
        <Menu className="h-5 w-5" />
      </button>
      <div className="min-w-0 flex-1 px-3">
        <p className="flex items-center gap-2 font-heading text-section text-text-primary">
          {props.titleIcon}
          <span className="min-w-0">{props.title}</span>
        </p>
        {props.subtitle && <div className="text-sm text-text-secondary">{props.subtitle}</div>}
      </div>
      <button
        type="button"
        onClick={props.onBellClick}
        aria-label={props.unreadCount ? t('header.notificationsUnread', { count: props.unreadCount }) : t('header.notifications')}
        className="relative -m-1 flex h-11 w-11 items-center justify-center rounded-full text-text-primary hover:bg-background"
      >
        <Bell className="h-5 w-5" />
        {props.unreadCount ? <BellBadge count={props.unreadCount} /> : null}
      </button>
    </header>
  );
}

/** The unread count on a bell. Also used by the partner app, which draws its own header. */
export function BellBadge({ count }: { count: number }) {
  return (
    <span
      data-testid="bell-badge"
      className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-micro leading-none text-text-inverse"
      aria-hidden="true"
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}

/**
 * A screen's title arriving, and staying alive: word by word, each rising out
 * of a soft blur into place, painted in the brand gradient (purple into
 * orange and back) that flows slowly through the letters; then a line the
 * title's full width draws itself in beneath, with a glint travelling along
 * it, and a small sparkle pops in at the end. By word, never by letter: Hindi
 * and Telugu letters join, and pulling them apart would break the words.
 * <p>
 * The words keep a real colour under the gradient, so they are read and
 * measured as the primary purple. Under reduced motion it is simply there.
 */
const TITLE_GRADIENT =
  'linear-gradient(90deg, var(--title-a), var(--title-b), var(--title-c), var(--title-b), var(--title-a))';

function AnimatedTitle({ text }: { text: string }) {
  const words = text.split(' ').filter(Boolean);
  const step = 70;
  const settled = words.length * step + 120;
  return (
    <span className="relative inline-flex items-start pb-2" data-testid="header-title">
      <span>
        {words.map((word, i) => (
          // An ordinary space between the words, outside each moving word, so
          // it is kept and read as a space.
          <Fragment key={i}>
            {i > 0 && ' '}
            <span className="inline-block motion-safe:animate-title-word" style={{ animationDelay: `${i * step}ms` }}>
              <span
                className="inline-block bg-[length:200%_auto] bg-clip-text text-primary [-webkit-text-fill-color:transparent] motion-safe:animate-text-shimmer"
                style={{ backgroundImage: TITLE_GRADIENT, animationDelay: `${i * -0.4}s` }}
              >
                {word}
              </span>
            </span>
          </Fragment>
        ))}
      </span>
      {/* A sparkle at the end of the title. */}
      <span aria-hidden="true" className="ml-1 mt-0.5 inline-flex motion-safe:animate-pop-in" style={{ animationDelay: `${settled}ms` }}>
        <Sparkles className="h-3.5 w-3.5 text-accent-orange motion-safe:animate-twinkle" />
      </span>
      {/* The line beneath, the title's full width: drawn in, then a glint along it. */}
      <span
        aria-hidden="true"
        className="absolute bottom-0 left-0 right-4 h-[3px] origin-left overflow-hidden rounded-full motion-safe:animate-fill-x"
        style={{ backgroundImage: TITLE_GRADIENT, animationDuration: '600ms', animationDelay: `${settled}ms` }}
      >
        <span className="absolute inset-y-0 left-0 w-1/3 bg-gradient-to-r from-transparent via-white/80 to-transparent motion-safe:animate-sheen" />
      </span>
    </span>
  );
}

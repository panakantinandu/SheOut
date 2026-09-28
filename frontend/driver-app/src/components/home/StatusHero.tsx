import { MapPinOff, Power, RefreshCw, ShieldCheck } from 'lucide-react';
import { Button, Card, IconCircle, SkeletonCard, StatusDot, brandIllustration, useTranslation } from '@sheout/design-system';
import type { HeroState } from '../../lib/driverStatus';

export interface StatusHeroProps {
  state: HeroState;
  toggling: boolean;
  onGoOnline: () => void;
  onGoOffline: () => void;
  onTurnOnLocation: () => void;
  onReviewVerification: () => void;
  /** A refused go-online, or a failed toggle, said inside the card it belongs to. */
  message?: string | null;
}

/**
 * The one card that says where she stands, and the one thing to do about
 * it. It replaces the welcome banner, the online card, the profile card and
 * the red "location not shared" alert.
 * <p>
 * COLOUR IS MEANING. Green is online and "go"; amber is "needs your
 * attention"; red is kept for SOS and destructive actions, so it is nowhere
 * here - going offline is not an emergency, and a missing location is not a
 * failure she caused. The purple surface is the neutral, brand state.
 * <p>
 * Go Online is the large green primary. Go Offline is deliberately quiet - a
 * secondary button, not full width, at the card's edge - so a thumb on a
 * handlebar mount does not end her shift by accident. Both are 56px tall.
 * <p>
 * The scooter illustration stays in its corner in the calm states and goes
 * in the amber ones, where the card has something to fix and nothing should
 * compete with it. A change of state cross-fades (one short fade, not a loop).
 */
export function StatusHero({ state, toggling, onGoOnline, onGoOffline, onTurnOnLocation, onReviewVerification, message }: StatusHeroProps) {
  const { t } = useTranslation();

  if (state === 'loading') return <SkeletonCard lines={3} label={t('home.loadingProfile')} />;

  const warning = state === 'noLocation' || state === 'reconnecting' || state === 'verification';
  const tone = warning ? 'warning' : state === 'live' ? 'success' : 'default';
  const goOffline = (
    <Button variant="secondary" size="lg" icon={<Power className="h-5 w-5" />} disabled={toggling} onClick={onGoOffline} data-testid="go-offline">
      {toggling ? '...' : t('home.goOffline')}
    </Button>
  );

  return (
    <Card tone={tone} className="relative overflow-hidden" data-testid="status-hero" data-state={state}>
      <div key={state} className="relative z-10 space-y-4 motion-safe:animate-fade-in">
        <div className={warning ? 'flex items-start gap-3' : 'min-h-[5.5rem] pr-24'}>
          {state === 'noLocation' && <IconCircle color="orange" tone="soft" icon={<MapPinOff />} />}
          {state === 'reconnecting' && <IconCircle color="orange" tone="soft" icon={<RefreshCw />} />}
          {state === 'verification' && <IconCircle color="orange" tone="soft" icon={<ShieldCheck />} />}
          <div className="min-w-0 flex-1">
            <p className="flex items-center gap-2 font-heading text-section text-text-primary" data-testid="hero-title">
              {(state === 'live' || state === 'connecting' || state === 'offline') && (
                <StatusDot live={state === 'live'} className="h-2.5 w-2.5" />
              )}
              <span className="break-words">{title(state, t)}</span>
            </p>
            <p className="mt-1 break-words text-sm text-text-secondary" data-testid="hero-body">{body(state, t)}</p>
          </div>
        </div>

        {message && <p className="text-sm font-medium text-accent-orange-strong" role="alert">{message}</p>}

        {state === 'offline' && (
          <Button variant="success" size="lg" fullWidth icon={<Power className="h-5 w-5" />} disabled={toggling} onClick={onGoOnline} data-testid="go-online">
            {toggling ? '...' : t('home.goOnline')}
          </Button>
        )}
        {state === 'noLocation' && (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button size="lg" icon={<MapPinOff className="h-5 w-5" />} onClick={onTurnOnLocation} data-testid="turn-on-location">
              {t('home.hero.noLocation.action')}
            </Button>
            {goOffline}
          </div>
        )}
        {state === 'verification' && (
          <Button size="lg" fullWidth onClick={onReviewVerification} data-testid="review-verification">
            {t('home.review')}
          </Button>
        )}
        {(state === 'live' || state === 'connecting' || state === 'reconnecting') && <div className="flex justify-end">{goOffline}</div>}
      </div>

      {!warning && (
        <img
          src={brandIllustration}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute -right-3 -top-2 h-28 w-28 object-contain"
        />
      )}
    </Card>
  );
}

function title(state: HeroState, t: (k: string) => string): string {
  switch (state) {
    case 'verification':
      return t('home.completeVerification');
    default:
      return t(`home.hero.${state}.title`);
  }
}

function body(state: HeroState, t: (k: string) => string): string {
  switch (state) {
    case 'verification':
      return t('home.verificationRequired');
    default:
      return t(`home.hero.${state}.body`);
  }
}

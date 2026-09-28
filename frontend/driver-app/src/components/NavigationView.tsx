import {
  ArrowUp,
  ArrowUpLeft,
  ArrowUpRight,
  CornerUpLeft,
  CornerUpRight,
  ExternalLink,
  Flag,
  List,
  Loader2,
  RotateCw,
  Undo2,
  Volume2,
  VolumeX,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useRef } from 'react';
import { LiveMap, i18next, useTranslation } from '@sheout/design-system';
import type { MapMarker } from '@sheout/design-system';
import type { RouteStep } from '../api/types';
import { describeStep, roundDistance, type ManeuverIcon, type NavState, type PreparedRoute } from '../lib/navigation';
import { useNavVoice, useWakeLock } from '../lib/navVoice';
import { PartnerSos } from './PartnerSos';

const ICONS: Record<ManeuverIcon, LucideIcon> = {
  straight: ArrowUp,
  'slight-left': ArrowUpLeft,
  'keep-left': ArrowUpLeft,
  left: CornerUpLeft,
  'sharp-left': CornerUpLeft,
  'slight-right': ArrowUpRight,
  'keep-right': ArrowUpRight,
  right: CornerUpRight,
  'sharp-right': CornerUpRight,
  uturn: Undo2,
  roundabout: RotateCw,
  arrive: Flag,
};

/** "Turn left" as it reads mid-sentence. Scripts without case are unchanged. */
const lowerFirst = (s: string) => s.charAt(0).toLocaleLowerCase() + s.slice(1);

/** Say the upcoming turn with its distance when it comes within this far... */
const PREPARE_METRES = 300;
/** ...and again, as "turn left now", this close. */
const NOW_METRES = 60;

export interface NavigationViewProps {
  phase: 'PICKUP' | 'DROP';
  bookingId?: string;
  destination: { lat: number; lng: number; label: string };
  prepared: PreparedRoute | null;
  nav: NavState | null;
  position: { lat: number; lng: number; heading?: number | null } | null;
  rerouting: boolean;
  routeError: boolean;
  onExit: () => void;
}

/**
 * Turn-by-turn navigation inside the app, the way ride apps do it: the map
 * follows her, the next turn is a large banner at the top, and time,
 * distance and arrival sit along the bottom. It opens by itself when she
 * accepts a trip (to the pickup) and again once the rider is on board (to
 * the drop), and closes by itself on arrival, where the trip screen has the
 * next step waiting - the pickup code, or End trip.
 * <p>
 * SOS stays on screen the whole time. Google Maps is still one tap away,
 * small, for anyone who prefers it; it is no longer the only way to get
 * there.
 * <p>
 * Google's logo and terms sit on the map's bottom edge, which is the top of
 * the bottom panel: the panel is below the map, never over it.
 */
export function NavigationView({ phase, bookingId, destination, prepared, nav, position, rerouting, routeError, onExit }: NavigationViewProps) {
  const { t, i18n } = useTranslation();
  const englishFor = (key: string, values?: Record<string, unknown>) => i18next.getFixedT('en')(key, values) as string;
  const voice = useNavVoice(i18n.language, englishFor);
  useWakeLock(true);

  const step: RouteStep | null = prepared && nav && nav.nextStep >= 0 ? prepared.steps[nav.nextStep] : null;
  const then: RouteStep | null = prepared && nav && nav.thenStep >= 0 ? prepared.steps[nav.thenStep] : null;
  const placeKey = phase === 'PICKUP' ? 'pickup' : 'drop';

  const sentence = (s: RouteStep | null) => {
    if (!s) return { text: t(`nav.arrive.${placeKey}`), key: `nav.arrive.${placeKey}`, values: {}, icon: 'arrive' as ManeuverIcon };
    const d = describeStep(s);
    if (d.key === 'arrive') return { text: t(`nav.arrive.${placeKey}`), key: `nav.arrive.${placeKey}`, values: {}, icon: d.icon };
    return { text: t(`nav.step.${d.key}`, d.values), key: `nav.step.${d.key}`, values: d.values, icon: d.icon };
  };
  const current = sentence(step);
  const Icon = ICONS[current.icon];
  const distance = nav ? roundDistance(nav.distanceToNextMetres) : null;
  const distanceText = distance ? t(distance.unit === 'km' ? 'nav.km' : 'nav.m', { n: distance.value }) : '';

  // Voice: each turn twice at most - with its distance as it comes up, and
  // "now" as she reaches it - and a first instruction when navigation opens.
  const spoken = useRef<{ step: number; stage: 'none' | 'prepare' | 'now' }>({ step: -2, stage: 'none' });
  useEffect(() => {
    if (!nav) return;
    const idx = nav.nextStep;
    const d = nav.distanceToNextMetres;
    const record = spoken.current;
    if (record.step !== idx) {
      spoken.current = { step: idx, stage: 'none' };
    }
    const r = spoken.current;
    const withDistance = () => {
      const rd = roundDistance(d);
      const dist = t(rd.unit === 'km' ? 'nav.km' : 'nav.m', { n: rd.value });
      const distEn = englishFor(rd.unit === 'km' ? 'nav.km' : 'nav.m', { n: rd.value });
      voice.say(t('nav.inDistance', { distance: dist, instruction: lowerFirst(current.text) }), 'nav.inDistance', {
        distance: distEn,
        instruction: lowerFirst(englishFor(current.key, current.values)),
      });
    };
    if (idx === -1 || step?.type === 'arrive') {
      if (d <= NOW_METRES && r.stage !== 'now') {
        voice.say(current.text, current.key, current.values);
        spoken.current = { step: idx, stage: 'now' };
      } else if (r.stage === 'none') {
        withDistance();
        spoken.current = { step: idx, stage: 'prepare' };
      }
      return;
    }
    if (d <= NOW_METRES && r.stage !== 'now') {
      voice.say(current.text, current.key, current.values);
      spoken.current = { step: idx, stage: 'now' };
    } else if (r.stage === 'none' && (d <= PREPARE_METRES || record.step === -2)) {
      withDistance();
      spoken.current = { step: idx, stage: 'prepare' };
    }
    // current and voice change with every render; the step and distance are what matter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nav?.nextStep, nav ? Math.round(nav.distanceToNextMetres / 10) : null]);

  const markers: MapMarker[] = [
    { key: 'destination', lat: destination.lat, lng: destination.lng, label: destination.label, kind: phase === 'PICKUP' ? 'pickup' : 'drop' },
  ];
  if (position) markers.push({ key: 'me', lat: position.lat, lng: position.lng, label: t('home.you'), kind: 'driver', heading: position.heading });

  const minutes = nav ? Math.max(1, Math.round(nav.remainingSeconds / 60)) : null;
  const remaining = nav ? roundDistance(nav.remainingMetres) : null;
  const arriveAt = nav ? new Date(Date.now() + nav.remainingSeconds * 1000).toLocaleTimeString(i18n.language, { hour: 'numeric', minute: '2-digit' }) : null;

  return (
    <div className="fixed inset-0 z-40 mx-auto flex max-w-md flex-col bg-surface" data-testid="navigation-view" data-phase={phase}>
      <div className="relative min-h-0 flex-1">
        <LiveMap markers={markers} route={nav?.remaining ?? prepared?.points} follow={position ?? destination} fill />

        {/* The next turn. */}
        <div className="pointer-events-none absolute inset-x-3 top-3 z-10 space-y-2">
          <div className="pointer-events-auto flex items-center gap-3 rounded-card bg-primary px-4 py-3 text-text-inverse shadow-float" data-testid="nav-banner">
            <Icon className="h-10 w-10 shrink-0" aria-hidden="true" />
            <div className="min-w-0 flex-1">
              {nav ? (
                <>
                  <p className="font-heading text-2xl font-bold leading-tight" data-testid="nav-distance">{distanceText}</p>
                  <p className="line-clamp-2 text-base font-semibold leading-snug" data-testid="nav-instruction">{current.text}</p>
                </>
              ) : (
                <p className="text-base font-semibold" data-testid="nav-instruction">
                  {routeError ? t('trip.routeError') : position ? t('trip.workingOutRoute') : t('trip.findingLocation')}
                </p>
              )}
            </div>
          </div>
          <div className="flex items-start justify-between gap-2">
            <div className="space-y-2">
              {then && (
                <p className="pointer-events-auto inline-flex items-center gap-1.5 rounded-full bg-primary-dark px-3 py-1.5 text-sm font-semibold text-text-inverse shadow-card" data-testid="nav-then">
                  {(() => {
                    const T = ICONS[sentence(then).icon];
                    return <T className="h-4 w-4" aria-hidden="true" />;
                  })()}
                  {t('nav.then')}
                </p>
              )}
              {rerouting && (
                <p className="pointer-events-auto flex items-center gap-1.5 rounded-full bg-surface px-3 py-1.5 text-sm font-semibold text-text-primary shadow-card" data-testid="nav-rerouting">
                  <Loader2 className="h-4 w-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
                  {t('nav.rerouting')}
                </p>
              )}
            </div>
            <div className="pointer-events-auto">
              <PartnerSos bookingId={bookingId} position={position} />
            </div>
          </div>
        </div>
      </div>

      {/* Time, distance, arrival - below the map, never over its logo. */}
      <div className="flex-none border-t border-border bg-surface px-4 pb-4 pt-3" data-testid="nav-footer">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="font-heading text-2xl font-bold text-accent-green-strong" data-testid="nav-eta">
              {minutes != null ? t('nav.minutes', { count: minutes }) : '...'}
            </p>
            <p className="truncate text-sm text-text-secondary">
              {remaining ? `${t(remaining.unit === 'km' ? 'nav.km' : 'nav.m', { n: remaining.value })} · ${t('nav.arriveAt', { time: arriveAt })}` : destination.label}
            </p>
          </div>
          {voice.supported && (
            <button
              type="button"
              onClick={voice.toggle}
              aria-label={voice.enabled ? t('nav.mute') : t('nav.unmute')}
              aria-pressed={!voice.enabled}
              className="flex h-12 w-12 items-center justify-center rounded-full bg-background text-text-primary"
              data-testid="nav-voice"
            >
              {voice.enabled ? <Volume2 className="h-5 w-5" aria-hidden="true" /> : <VolumeX className="h-5 w-5" aria-hidden="true" />}
            </button>
          )}
          <button
            type="button"
            onClick={onExit}
            className="flex h-12 items-center gap-2 rounded-full bg-background px-4 text-sm font-semibold text-text-primary"
            data-testid="nav-exit"
          >
            <List className="h-5 w-5" aria-hidden="true" />
            {t('nav.tripDetails')}
          </button>
        </div>
        <div className="mt-3 flex items-center gap-2 border-t border-border pt-3">
          <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${phase === 'PICKUP' ? 'bg-primary' : 'bg-accent-orange'}`} aria-hidden="true" />
          <p className="min-w-0 flex-1 truncate text-sm text-text-primary">
            <span className="font-semibold">{t(phase === 'PICKUP' ? 'trip.pickup' : 'trip.drop')}</span> · {destination.label}
          </p>
          {/* Still there for anyone who prefers it: small, never the main way. */}
          <a
            href={`https://www.google.com/maps/dir/?api=1&destination=${destination.lat},${destination.lng}&travelmode=driving`}
            target="_blank"
            rel="noopener noreferrer"
            className="flex shrink-0 items-center gap-1 text-xs font-semibold text-primary"
            data-testid="nav-google-maps"
          >
            {t('nav.googleMaps')}
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        </div>
      </div>
    </div>
  );
}

import { BadgeCheck, Bike, MapPin, ShieldAlert, Siren, Wallet } from 'lucide-react';
import { useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, IconCircle, brandIllustration, useTranslation } from '@sheout/design-system';
import { usersApi } from '../api/client';

interface Slide {
  key: string;
  icon: ReactNode;
  title: string;
  body: string;
  points?: { icon: ReactNode; label: string }[];
}

/**
 * The four things worth knowing before the first ride, once.
 * <p>
 * Shown after a new account finishes its profile, and never again: the server
 * records that it was seen the moment she lands here, whether she reads it or
 * skips it, because an introduction that comes back is an obstacle.
 * <p>
 * SWIPEABLE WITHOUT A CARRIER LIBRARY. It is a scroll container with CSS
 * snap points - the browser's own horizontal scrolling, so it follows her
 * finger exactly, works with a trackpad and a screen reader, and costs
 * nothing to load. A carousel library for four static panels would be
 * thirty kilobytes to reimplement scrolling.
 * <p>
 * Skip is on every panel, in the corner, from the first moment. Somebody who
 * wants to book a ride should never have to read four screens to do it.
 */
export function Onboarding() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const trackRef = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);

  const slides: Slide[] = [
    {
      key: 'what',
      icon: <Bike />,
      title: t('onboarding.what.title'),
      body: t('onboarding.what.body'),
      points: [
        { icon: <Bike />, label: t('home.serviceRide') },
        { icon: <MapPin />, label: t('home.serviceParcel') },
      ],
    },
    {
      key: 'safety',
      icon: <Siren />,
      title: t('onboarding.safety.title'),
      body: t('onboarding.safety.body'),
      points: [
        { icon: <BadgeCheck />, label: t('onboarding.safety.verified') },
        { icon: <ShieldAlert />, label: t('onboarding.safety.contacts') },
        { icon: <Siren />, label: t('onboarding.safety.sos') },
      ],
    },
    {
      key: 'booking',
      icon: <MapPin />,
      title: t('onboarding.booking.title'),
      body: t('onboarding.booking.body'),
    },
    {
      key: 'ready',
      icon: <Wallet />,
      title: t('onboarding.ready.title'),
      body: t('onboarding.ready.body'),
    },
  ];

  /** Recorded on the way out, by either door. */
  async function finish() {
    await usersApi.markOnboardingSeen().catch(() => undefined);
    navigate('/home', { replace: true });
  }

  function goTo(next: number) {
    const track = trackRef.current;
    if (!track) return;
    track.scrollTo({ left: track.clientWidth * next, behavior: 'smooth' });
  }

  const last = index === slides.length - 1;

  return (
    <div className="relative flex min-h-screen flex-col bg-brand-wash">
      <div className="flex justify-end px-screen pt-4">
        <button
          type="button"
          onClick={finish}
          className="rounded-full px-3 py-1.5 text-sm font-semibold text-text-secondary"
          data-testid="onboarding-skip"
        >
          {t('onboarding.skip')}
        </button>
      </div>

      <div
        ref={trackRef}
        onScroll={(e) => {
          const el = e.currentTarget;
          setIndex(Math.round(el.scrollLeft / el.clientWidth));
        }}
        className="flex flex-1 snap-x snap-mandatory overflow-x-auto scroll-smooth [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        data-testid="onboarding-track"
      >
        {slides.map((slide) => (
          <section
            key={slide.key}
            className="flex w-full shrink-0 snap-center flex-col items-center justify-center gap-5 px-screen text-center"
            data-testid="onboarding-slide"
          >
            <div className="relative flex items-center justify-center">
              <span className="absolute h-40 w-40 rounded-full bg-accent-orange/15 blur-2xl" aria-hidden="true" />
              <img src={brandIllustration} alt="" className="relative h-36 w-36 object-contain motion-safe:animate-float" />
            </div>
            <IconCircle tone="soft" size="md" icon={slide.icon} />
            <h1 className="font-heading text-2xl font-bold text-text-primary">{slide.title}</h1>
            <p className="max-w-xs text-sm leading-relaxed text-text-secondary">{slide.body}</p>
            {slide.points && (
              <div className="flex flex-wrap items-center justify-center gap-2">
                {slide.points.map((point) => (
                  <span
                    key={point.label}
                    className="flex items-center gap-1.5 rounded-full bg-surface/80 px-3 py-1.5 text-xs font-medium text-text-primary shadow-sm"
                  >
                    <span className="text-primary [&>svg]:h-3.5 [&>svg]:w-3.5">{point.icon}</span>
                    {point.label}
                  </span>
                ))}
              </div>
            )}
          </section>
        ))}
      </div>

      <div className="space-y-4 px-screen pb-10 pt-4">
        <div className="flex justify-center gap-2" data-testid="onboarding-dots">
          {slides.map((slide, i) => (
            <button
              key={slide.key}
              type="button"
              onClick={() => goTo(i)}
              aria-label={t('onboarding.goTo', { number: i + 1 })}
              aria-current={i === index}
              className={
                i === index
                  ? 'h-2 w-6 rounded-full bg-primary transition-all'
                  : 'h-2 w-2 rounded-full bg-primary/25 transition-all'
              }
            />
          ))}
        </div>
        <Button fullWidth onClick={() => (last ? finish() : goTo(index + 1))} data-testid="onboarding-next">
          {last ? t('onboarding.start') : t('common.next')}
        </Button>
      </div>
    </div>
  );
}

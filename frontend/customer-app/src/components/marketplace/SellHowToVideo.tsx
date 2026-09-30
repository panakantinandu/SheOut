import { Play, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button, Overlay, useTranslation } from '@sheout/design-system';

const VIDEO = '/videos/sell-on-sheout.mp4';
const POSTER = '/videos/sell-on-sheout-poster.webp';

/**
 * "Watch how it works": the one-minute walk-through of opening a shop,
 * under Start selling on the Marketplace.
 * <p>
 * A picture and a play button, not an autoplaying video in the page: the
 * file is 2.6 MB, and a woman scrolling the Marketplace on mobile data
 * should not pay for it unless she asks. Tapped, it opens full screen - it
 * is a phone-shaped video, made to be watched upright - and it ends on
 * Start selling, so what she just watched is one tap away.
 */
export function SellHowToVideo({ onStart }: { onStart: () => void }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="mt-3 flex w-full items-center gap-3 rounded-2xl bg-surface/80 p-2 text-left ring-1 ring-primary/15 transition-transform duration-100 motion-safe:active:scale-[0.98]"
        data-testid="market-sell-video"
      >
        <span className="relative h-16 w-12 shrink-0 overflow-hidden rounded-xl bg-[#1B1030]">
          <img src={POSTER} alt="" loading="lazy" draggable={false} className="h-full w-full object-cover" />
          <span className="absolute inset-0 flex items-center justify-center bg-black/20">
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-white/90 text-primary shadow-float motion-safe:animate-glow-brand">
              <Play className="ml-0.5 h-3.5 w-3.5 fill-current" aria-hidden="true" />
            </span>
          </span>
        </span>
        <span className="min-w-0 flex-1">
          <span className="block font-heading text-sm text-text-primary">{t('seller.discover.sell.watch')}</span>
          <span className="block text-caption text-text-secondary">{t('seller.discover.sell.watchMeta')}</span>
        </span>
      </button>

      {open && (
        <HowToPlayer
          onClose={() => setOpen(false)}
          onStart={() => {
            setOpen(false);
            onStart();
          }}
        />
      )}
    </>
  );
}

function HowToPlayer({ onClose, onStart }: { onClose: () => void; onStart: () => void }) {
  const { t } = useTranslation();
  const video = useRef<HTMLVideoElement>(null);
  const [ended, setEnded] = useState(false);

  useEffect(() => {
    video.current?.play().catch(() => undefined);
  }, []);

  return (
    <Overlay open label={t('seller.discover.sell.videoTitle')} onDismiss={onClose} className="px-4">
      {/* Darker than a dialog's dim: this is for watching. */}
      <div aria-hidden="true" className="pointer-events-none fixed inset-0 bg-[#0E0A1B]/85" />
      <div className="relative flex w-full max-w-sm flex-col items-center gap-4 motion-safe:animate-pop-in" data-testid="market-sell-player">
        <button
          type="button"
          onClick={onClose}
          aria-label={t('seller.discover.sell.videoClose')}
          className="absolute -top-1 right-0 z-10 flex h-10 w-10 items-center justify-center rounded-full bg-white/15 text-white backdrop-blur"
        >
          <X className="h-5 w-5" aria-hidden="true" />
        </button>
        <video
          ref={video}
          src={VIDEO}
          poster={POSTER}
          controls
          playsInline
          muted
          preload="metadata"
          onEnded={() => setEnded(true)}
          onPlay={() => setEnded(false)}
          className="mt-10 max-h-[72vh] w-auto max-w-full rounded-card bg-[#1B1030] shadow-xl"
        />
        <Button fullWidth size="lg" onClick={onStart} className={ended ? 'motion-safe:animate-glow-brand' : undefined} data-testid="market-sell-player-start">
          {t('seller.discover.sell.cta')}
        </Button>
      </div>
    </Overlay>
  );
}

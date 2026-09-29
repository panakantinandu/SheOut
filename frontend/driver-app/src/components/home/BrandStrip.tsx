import { useTranslation } from '@sheout/design-system';

/**
 * SheOut Partner, by name, at the top of Home. Partners asked where they
 * were: nothing on the screen said "SheOut". The app's own icon floats
 * gently beside the wordmark, in the brand purple-to-orange, and "Partner"
 * under it, with a pulsing dot, says which app this is. Compact, so it fits the app bar beside the bell and SOS on any phone.
 */
export function BrandStrip() {
  const { t } = useTranslation();
  return (
    <div className="flex min-w-0 items-center gap-2 motion-safe:animate-fade-slide-in" data-testid="brand-strip">
      <img
        src="/icons/icon-192.png"
        alt=""
        aria-hidden="true"
        draggable={false}
        className="h-9 w-9 shrink-0 select-none rounded-xl shadow-lift motion-safe:animate-float"
      />
      <span className="flex min-w-0 flex-col">
        {/* The brand gradient, painted through the letters; the text keeps a real
            colour underneath for screen readers and contrast tools. The orange end
            is the deep one, so "Out" reads in sunlight too. */}
        <span className="self-start bg-gradient-to-r from-primary via-primary-mid to-accent-orange-strong bg-clip-text font-heading text-[1.3125rem] font-extrabold leading-none tracking-tight text-primary [-webkit-text-fill-color:transparent]">
          SheOut
        </span>
        <span className="mt-0.5 inline-flex items-center gap-1 text-micro font-bold uppercase tracking-[0.12em] text-primary">
          <span className="relative flex h-1.5 w-1.5">
            <span className="absolute inset-0 rounded-full bg-accent-orange motion-safe:animate-pulse-ring" />
            <span className="relative h-1.5 w-1.5 rounded-full bg-accent-orange" />
          </span>
          {t('home.brandPartner')}
        </span>
      </span>
    </div>
  );
}
